// Takes the five landing-page screenshots from the LOCAL app, after
// seed-day.ts has put a day of sales in it. Needs both servers running
// (frontend on :3001, backend on :8080).
//
//   npm i && npx playwright install chromium     # once
//   npx tsx scripts/screenshots.ts
//
// Output goes to public/landing/<slot>.png, replacing the grey placeholders:
//   counter    laptop, 1440x900   staff sales screen, order beside the tiles
//   day-close  laptop, 1440x900   owner's day close for today
//   stock      laptop, 1440x900   owner's stock levels, needs-attention first
//   low-stock  laptop, 1440x900   owner's dashboard, low-stock list
//   ledger     laptop, 1440x900   the voided receipt (record stays visible)
// All at deviceScaleFactor 2. The owner screens are one narrow centred
// column, so those four are cropped to the middle 960x600 (still 16:10).
//
// The owner is signed in with a one-time Clerk sign-in ticket for
// SEED_OWNER_EMAIL (development Clerk instance only), so no password or
// verification code is typed. The shop device it pairs is revoked at the end.
import { chromium, type Browser, type BrowserContext, type Page } from "playwright";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { DEVICE_TOKEN_KEY } from "../lib/deviceToken";
import { APP, STAFF, api, guardLocal, owner, ownerSignInTicket, pairDevice, staffLogin, todayUtc } from "./lib";

const OUT = resolve(__dirname, "../public/landing");
const DESKTOP = { viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 };

type Saleable = { productId: number; name: string; unit: "KG" | "UNIT"; price: number };

async function settle(page: Page): Promise<void> {
  await page.waitForLoadState("networkidle");
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
}

const OWNER_CROP = { x: 240, y: 0, width: 960, height: 600 };

async function shot(page: Page, slot: string, clip?: typeof OWNER_CROP): Promise<void> {
  await settle(page);
  // next dev draws its indicator (and error overlay) in a <nextjs-portal>.
  await page.addStyleTag({ content: "nextjs-portal { display: none !important; }" });
  await page.screenshot({ path: resolve(OUT, `${slot}.png`), clip });
  console.log(`  saved public/landing/${slot}.png`);
}

// A believable order: one kg item and three everyday units.
function chooseOrder(products: Saleable[]): string[] {
  const kg = products.find((p) => p.unit === "KG");
  const units = products.filter((p) => p.unit === "UNIT" && p.price >= 5 && p.price <= 70);
  const picks = [0, 1, 2].map((i) => units[(i * 7 + 3) % units.length]);
  return [kg, ...picks].filter((p): p is Saleable => Boolean(p)).map((p) => p.name);
}

async function staffSalesScreen(context: BrowserContext, deviceToken: string, order: string[]): Promise<Page> {
  await context.addInitScript(([key, token]) => localStorage.setItem(key, token), [DEVICE_TOKEN_KEY, deviceToken]);
  const page = await context.newPage();
  const seen: string[] = [];
  page.on("console", (m) => m.type() === "error" && seen.push(`console: ${m.text()}`));
  page.on("pageerror", (e) => seen.push(`pageerror: ${e.message}`));
  page.on("requestfailed", (r) => seen.push(`request failed: ${r.url()} ${r.failure()?.errorText}`));
  await page.goto(`${APP}/shop`);
  try {
    await page.getByRole("button", { name: STAFF[0].name }).click({ timeout: 15_000 });
  } catch {
    const path = resolve(OUT, "_debug.png");
    await page.screenshot({ path });
    const text = (await page.locator("body").innerText()).slice(0, 300).replace(/\s+/g, " ");
    throw new Error(`Could not find ${STAFF[0].name} on ${page.url()}. Page says: "${text}". ${seen.join(" | ")}. Saved ${path}`);
  }
  for (const digit of STAFF[0].pin) await page.getByRole("button", { name: digit, exact: true }).click();
  await page.getByPlaceholder("Search products…").waitFor();
  for (const name of order) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    await page.locator("button", { hasText: new RegExp(`^${escaped}\\s*GH₵`) }).first().click();
  }
  return page;
}

async function signInOwner(context: BrowserContext): Promise<Page> {
  const page = await context.newPage();
  await page.goto(`${APP}/sign-in?__clerk_ticket=${await ownerSignInTicket()}`);
  try {
    await page.waitForURL((u) => !u.pathname.startsWith("/sign-in"), { timeout: 30_000 });
  } catch {
    throw new Error("Clerk sign-in ticket did not sign the owner in (still on /sign-in). Is the frontend using the same Clerk dev instance as CLERK_SECRET_KEY?");
  }
  await settle(page);
  return page;
}

async function main() {
  guardLocal();
  const today = todayUtc();
  const close = await owner<{ sales: { receiptNumber: number; voided: boolean }[] }>(`/api/owner/day-close/${today}`);
  if (close.sales.length < 20) throw new Error("Today has hardly any sales. Run: npx tsx scripts/seed-day.ts");
  const voided = close.sales.filter((s) => s.voided).sort((a, b) => b.receiptNumber - a.receiptNumber)[0];
  if (!voided) throw new Error("Today has no voided sale for the ledger shot. Run the seed first.");

  mkdirSync(OUT, { recursive: true });
  const device = await pairDevice();
  let browser: Browser | undefined;
  try {
    const session = await staffLogin(device.deviceToken, STAFF[0].name, STAFF[0].pin);
    const products = await api<Saleable[]>("/api/staff/sales", { auth: { device: device.deviceToken, staff: session } });
    const order = chooseOrder(products);
    console.log(`Order for the staff shots: ${order.join(", ")}`);

    browser = await chromium.launch();

    console.log("Staff sales screen:");
    const counterCtx = await browser.newContext(DESKTOP);
    await shot(await staffSalesScreen(counterCtx, device.deviceToken, order), "counter");

    console.log("Owner screens (laptop):");
    const ownerCtx = await browser.newContext(DESKTOP);
    const page = await signInOwner(ownerCtx);
    for (const [slot, path] of [
      ["day-close", `/owner/day-close?date=${today}`],
      ["stock", "/owner/stock/levels"],
      ["low-stock", "/owner"],
      ["ledger", `/owner/sales/${voided.receiptNumber}`],
    ] as const) {
      await page.goto(`${APP}${path}`);
      await shot(page, slot, OWNER_CROP);
    }
  } finally {
    await browser?.close();
    await device.revoke();
  }
  console.log("Done. Check the five images in public/landing/ before committing them: they show real names and totals from your local data.");
}

main().catch((e) => {
  console.error(`\nScreenshots failed: ${e instanceof Error ? e.message : e}`);
  process.exit(1);
});
