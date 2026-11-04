// Seeds one realistic trading day (today, UTC) into the LOCAL database by
// calling the backend's real endpoints: nothing is inserted directly.
//
//   npx tsx scripts/seed-day.ts --dry-run   # read-only: shows the plan
//   npx tsx scripts/seed-day.ts             # writes it
//   npx tsx scripts/seed-day.ts --force     # run even if today already has sales
//
// What it does, in order: tops up the catalogue if it is thin, creates two
// counter staff, pairs a throwaway device, receives enough stock that the day
// ends with 3-4 best sellers below their alert level, rings up 62 sales
// (kg including 0.5 kg, units, a few flat GH₵ discounts), voids one mis-keyed
// sale, records one write-off, then reads the day close. The device is
// revoked at the end.
//
// Sales are stamped by the server when they arrive, so the whole day lands
// within a minute or two of running the script. That is a limit of using the
// real endpoints, and it is why receipt times will look bunched up.
import { STAFF, api, guardLocal, owner, pairDevice, staffLogin, todayUtc } from "./lib";

const DRY = process.argv.includes("--dry-run");
const FORCE = process.argv.includes("--force");
const SALE_COUNT = 62;
const MISTAKE_AT = 30; // index of the mis-keyed sale; the correction follows it

type Product = {
  id: number;
  name: string;
  unit: "KG" | "UNIT";
  price: number;
  alertLevel: number;
  cartonWeight: number | null;
  active: boolean;
};
type Level = { productId: number; currentBalance: number };
type PlanLine = { product: Product; qty: number; discount: number };
type PlanSale = { staff: number; lines: PlanLine[]; kind?: "mistake" | "correction" };
type DayClose = {
  totalSales: number;
  totalDiscounts: number;
  expectedCash: number;
  sales: { receiptNumber: number; staffName: string; total: number; voided: boolean }[];
};

// Used only to top up a thin catalogue. Existing products are never changed
// (apart from an alert level of 0, see below).
const CATALOGUE: Omit<Product, "id" | "active" | "cartonWeight">[] = [
  { name: "Frozen chicken", unit: "KG", price: 42, alertLevel: 10 },
  { name: "Turkey tail", unit: "KG", price: 58, alertLevel: 8 },
  { name: "Local rice", unit: "KG", price: 18, alertLevel: 15 },
  { name: "Gari", unit: "KG", price: 9, alertLevel: 15 },
  { name: "Tomato paste tin", unit: "UNIT", price: 4.5, alertLevel: 24 },
  { name: "Cooking oil 1L", unit: "UNIT", price: 38, alertLevel: 12 },
  { name: "Sardines tin", unit: "UNIT", price: 12, alertLevel: 20 },
  { name: "Evaporated milk tin", unit: "UNIT", price: 9, alertLevel: 20 },
  { name: "Milo 400g", unit: "UNIT", price: 48, alertLevel: 8 },
  { name: "Shito jar", unit: "UNIT", price: 25, alertLevel: 6 },
  { name: "Bottled water 500ml", unit: "UNIT", price: 3, alertLevel: 36 },
  { name: "Seasoning cubes pack", unit: "UNIT", price: 2, alertLevel: 30 },
  { name: "Spaghetti pack", unit: "UNIT", price: 8, alertLevel: 15 },
  { name: "Soft drink 50cl", unit: "UNIT", price: 7, alertLevel: 24 },
];
const MIN_KG = 3;
const MIN_UNIT = 8;

// Deterministic, so a dry run and the real run agree.
function rng(seed: number) {
  return () => {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rand = rng(Number(todayUtc().replaceAll("-", "")));
const pickWeighted = <T,>(items: T[], weights: number[]): T => {
  let r = rand() * weights.reduce((a, b) => a + b, 0);
  for (let i = 0; i < items.length; i++) {
    r -= weights[i];
    if (r <= 0) return items[i];
  }
  return items[items.length - 1];
};
const round2 = (n: number) => Math.round(n * 100) / 100;
const gh = (n: number) => `GH₵${n.toFixed(2)}`;
const log = (s: string) => console.log(s);

function planSales(products: Product[], mistakeProduct: Product, forced: Map<Product, number>): PlanSale[] {
  // A few products sell much more than the rest.
  const ranked = [...products].sort(() => rand() - 0.5);
  // ...and dear items (a 25 kg bag of rice) sell less often than staples.
  const weights = ranked.map((p, i) => 1 / Math.pow(i + 1, 0.5) / (1 + p.price / 25));
  const qtyFor = (p: Product) =>
    p.unit === "KG"
      ? pickWeighted([0.5, 1, 1.5, 2, 3, 0.75, 2.25], [20, 30, 12, 20, 6, 6, 6])
      : pickWeighted([1, 2, 3, 4, 6], [50, 30, 12, 5, 3]);

  const sales: PlanSale[] = [];
  for (let i = 0; i < SALE_COUNT; i++) {
    const staff = i % 2 === 0 ? (rand() < 0.7 ? 0 : 1) : rand() < 0.7 ? 1 : 0;
    if (i === MISTAKE_AT || i === MISTAKE_AT + 1) {
      const kg = mistakeProduct;
      const mistake = i === MISTAKE_AT;
      sales.push({
        staff: sales[i - 1]?.kind === "mistake" ? sales[i - 1].staff : staff,
        lines: [{ product: kg, qty: mistake ? 10 : 1, discount: 0 }], // keyed 10 kg instead of 1 kg
        kind: mistake ? "mistake" : "correction",
      });
      continue;
    }
    const wanted = pickWeighted([1, 2, 3, 4, 5], [35, 30, 20, 10, 5]);
    const chosen: Product[] = [];
    while (chosen.length < Math.min(wanted, ranked.length)) {
      const p = pickWeighted(ranked, weights);
      if (!chosen.includes(p)) chosen.push(p);
    }
    sales.push({ staff, lines: chosen.map((p) => ({ product: p, qty: qtyFor(p), discount: 0 })) });
  }

  // Products meant to end the day low must actually sell down to that level:
  // top their sold quantity up to the target by growing existing lines or
  // adding the product to other sales, a little at a time.
  const soldIn = (p: Product) =>
    round2(sales.filter((s) => s.kind !== "mistake").flatMap((s) => s.lines).filter((l) => l.product === p).reduce((a, l) => a + l.qty, 0));
  for (const [p, target] of forced) {
    for (let guard = 0; soldIn(p) < target && guard < 2000; guard++) {
      const remaining = round2(target - soldIn(p));
      const chunk = Math.min(remaining, p.unit === "KG" ? pickWeighted([0.5, 1, 1.5], [3, 3, 1]) : pickWeighted([1, 2], [3, 1]));
      const s = sales[Math.floor(rand() * sales.length)];
      if (s.kind) continue;
      const line = s.lines.find((l) => l.product === p);
      if (line) line.qty = round2(line.qty + chunk);
      else if (s.lines.length < 6) s.lines.push({ product: p, qty: chunk, discount: 0 });
    }
  }

  // Five flat discounts, each well under its line total.
  let given = 0;
  for (let guard = 0; given < 5 && guard < 500; guard++) {
    const i = Math.floor(rand() * sales.length);
    if (sales[i].kind) continue;
    const line = sales[i].lines[Math.floor(rand() * sales[i].lines.length)];
    if (line.discount) continue;
    const raw = line.qty * line.product.price;
    const options = [1, 2, 5].filter((d) => d <= raw * 0.4);
    if (!options.length) continue;
    line.discount = options[Math.floor(rand() * options.length)];
    given++;
  }
  return sales;
}

async function ensureCatalogue(existing: Product[]): Promise<Product[]> {
  const products = existing.filter((p) => p.active);
  const have = new Set(products.map((p) => p.name.toLowerCase()));
  const count = (u: Product["unit"]) => products.filter((p) => p.unit === u).length;
  for (const c of CATALOGUE) {
    if (count("KG") >= MIN_KG && count("UNIT") >= MIN_UNIT) break;
    const needed = c.unit === "KG" ? count("KG") < MIN_KG : count("UNIT") < MIN_UNIT;
    if (!needed || have.has(c.name.toLowerCase())) continue;
    log(`  + product ${c.name} (${c.unit}, ${gh(c.price)})${DRY ? "  [dry run]" : ""}`);
    if (DRY) {
      products.push({ ...c, id: -products.length - 1, cartonWeight: null, active: true });
    } else {
      products.push(await owner<Product>("/api/owner/products", { ...c, cartonWeight: null }));
    }
    have.add(c.name.toLowerCase());
  }
  return products;
}

async function ensureStaff(): Promise<void> {
  const current = await owner<{ id: number; name: string; active: boolean }[]>("/api/owner/staff");
  for (const s of STAFF) {
    const found = current.find((c) => c.name === s.name);
    if (found && !found.active) {
      throw new Error(`${s.name} exists but is deactivated. Edit STAFF in scripts/lib.ts or reactivate by hand.`);
    }
    log(`  ${found ? "reset PIN for" : "+ staff"} ${s.name}${DRY ? "  [dry run]" : ""}`);
    if (DRY) continue;
    if (found) await owner(`/api/owner/staff/${found.id}/reset-pin`, { pin: s.pin });
    else await owner("/api/owner/staff", { name: s.name, pin: s.pin });
  }
}

async function main() {
  guardLocal();
  const today = todayUtc();
  log(`Seeding ${today} (UTC) into ${DRY ? "nothing (dry run)" : "the local database"}.`);

  const before = await owner<DayClose>(`/api/owner/day-close/${today}`);
  if (before.sales.length > 0 && !FORCE) {
    throw new Error(`Today already has ${before.sales.length} sale(s). Run with --force to add a day on top.`);
  }

  log("Catalogue:");
  const products = await ensureCatalogue(await owner<Product[]>("/api/owner/products"));
  log("Staff:");
  await ensureStaff();

  const levels = new Map((await owner<Level[]>("/api/owner/stock/levels")).map((l) => [l.productId, l.currentBalance] as const));
  const balance = (p: Product) => levels.get(p.id) ?? 0;
  const alertFor = (p: Product) => (p.alertLevel > 0 ? p.alertLevel : p.unit === "KG" ? 8 : 12);
  const lowTarget = (p: Product) => (p.unit === "KG" ? round2(alertFor(p) * 0.4) : Math.max(1, Math.floor(alertFor(p) * 0.4)));
  const toSell = (p: Product) => round2(balance(p) - lowTarget(p)); // sold quantity that leaves it LOW

  // Who ends low: the products that are cheapest to sell down (little stock
  // above the alert level, and not absurd revenue), two by kg if possible.
  // The mis-keyed 10 kg sale goes on the kg product with the most stock.
  const kgs = products.filter((p) => p.unit === "KG").sort((a, b) => toSell(a) - toSell(b));
  const mistakeProduct = kgs[kgs.length - 1];
  const lowKg = kgs.slice(0, Math.min(2, kgs.length - 1));
  const pool = products
    .filter((p) => p.unit === "UNIT" && toSell(p) > 0 && toSell(p) <= 30 && toSell(p) * p.price <= 700)
    .sort((a, b) => toSell(a) - toSell(b))
    .slice(0, 12)
    .sort(() => rand() - 0.5);
  const low = [...lowKg, ...pool.slice(0, 4 - lowKg.length)];
  if (low.length < 3) log(`  note: only ${low.length} product(s) can be sold down to a low level in one day.`);
  const forced = new Map(low.map((p) => [p, toSell(p)] as const));

  const plan = planSales(products, mistakeProduct, forced);
  const sold = new Map<number, number>(); // net of the voided sale
  for (const s of plan) {
    if (s.kind === "mistake") continue;
    for (const l of s.lines) sold.set(l.product.id, round2((sold.get(l.product.id) ?? 0) + l.qty));
  }

  // The write-off: a healthy kg product if there is one (spoilage), else units.
  const healthy = products.filter((p) => !low.includes(p) && p !== mistakeProduct);
  const writeOff = healthy.find((p) => p.unit === "KG") ?? healthy.find((p) => p.unit === "UNIT")!;
  const woQty = writeOff.unit === "KG" ? 1.5 : 2;
  const woNote = writeOff.unit === "KG" ? "Spoiled in storage" : "Dented, not fit to sell";

  log("Stock to receive (so the day ends the way a real one would):");
  const receives: { p: Product; qty: number; why: string }[] = [];
  const alertFixes: Product[] = [];
  for (const p of products) {
    const isLow = low.includes(p);
    const a = alertFor(p);
    const used = (sold.get(p.id) ?? 0) + (p === writeOff ? woQty : 0);
    const target = isLow ? lowTarget(p) : Math.max(Math.ceil(a * 1.5), a + 2) + (p === mistakeProduct ? 10 : 0);
    const need = round2(target + used - balance(p));
    if (isLow && p.alertLevel <= 0) alertFixes.push(p);
    if (need > 0) {
      receives.push({ p, qty: p.unit === "UNIT" ? Math.ceil(need) : need, why: isLow ? "ends LOW" : "ends healthy" });
    }
  }
  for (const r of receives) log(`  ${r.p.name}: receive ${r.qty} ${r.p.unit === "KG" ? "kg" : "units"} (${r.why})`);
  for (const p of alertFixes) log(`  ${p.name}: alert level was 0, setting ${alertFor(p)}`);
  log(`Planned: ${plan.length} sales (${plan.filter((s) => s.staff === 0).length} by ${STAFF[0].name}, ${plan.filter((s) => s.staff === 1).length} by ${STAFF[1].name}), ` +
    `${plan.flatMap((s) => s.lines).filter((l) => l.discount).length} discounted lines, 1 voided, 1 write-off (${woQty} ${writeOff.name}).`);
  const gross = plan.filter((s) => s.kind !== "mistake").flatMap((s) => s.lines).reduce((a, l) => a + l.qty * l.product.price - l.discount, 0);
  const top = [...sold].sort((a, b) => b[1] - a[1]).slice(0, 3).map(([id, q]) => `${products.find((p) => p.id === id)?.name} ${q}`);
  log(`Estimated sales ${gh(gross)}. Top sellers: ${top.join("; ")}.`);
  log(`Expected to end LOW: ${low.map((p) => p.name).join(", ") || "none"}`);
  if (DRY) {
    log("Dry run: nothing was written.");
    return;
  }

  for (const p of alertFixes) {
    await owner(`/api/owner/products/${p.id}`, { name: p.name, unit: p.unit, price: p.price, alertLevel: alertFor(p), cartonWeight: p.cartonWeight });
    p.alertLevel = alertFor(p);
  }
  for (const r of receives) await owner("/api/owner/stock/receive", { productId: r.p.id, actualQuantity: r.qty });

  const device = await pairDevice();
  try {
    const sessions = await Promise.all(STAFF.map((s) => staffLogin(device.deviceToken, s.name, s.pin)));
    let voidReceipt: number | undefined;
    let n = 0;
    for (const s of plan) {
      const sale = await api<{ receiptNumber: number }>("/api/staff/sales", {
        body: { lines: s.lines.map((l) => ({ productId: l.product.id, quantity: l.qty, discount: l.discount })) },
        auth: { device: device.deviceToken, staff: sessions[s.staff] },
      });
      if (s.kind === "mistake") voidReceipt = sale.receiptNumber;
      if (++n % 10 === 0) log(`  ${n}/${plan.length} sales`);
    }
    if (voidReceipt !== undefined) {
      await owner(`/api/owner/sales/${voidReceipt}/void`, { note: "Keyed 10 kg instead of 1 kg" });
      log(`  voided receipt ${voidReceipt} (the mis-keyed one)`);
    }
    await owner("/api/owner/stock/write-off", { productId: writeOff.id, quantity: woQty, note: woNote });
    log(`  wrote off ${woQty} ${writeOff.unit === "KG" ? "kg" : "units"} of ${writeOff.name}`);
  } finally {
    await device.revoke();
  }

  const close = await owner<DayClose>(`/api/owner/day-close/${today}`);
  const live = close.sales.filter((s) => !s.voided);
  log(`Day close ${today}: ${live.length} sales (+${close.sales.length - live.length} voided), ` +
    `sales ${gh(close.totalSales)}, discounts ${gh(close.totalDiscounts)}, expected cash ${gh(close.expectedCash)}`);
  const after = await owner<(Level & { name: string; alertLevel: number; status: string })[]>("/api/owner/stock/levels");
  for (const l of after.filter((x) => x.status !== "OK")) log(`  ${l.status.padEnd(8)} ${l.name}: ${l.currentBalance} (alert ${l.alertLevel})`);
  log("Done. Next: npx tsx scripts/screenshots.ts");
}

main().catch((e) => {
  console.error(`\nSeed failed: ${e instanceof Error ? e.message : e}`);
  process.exit(1);
});
