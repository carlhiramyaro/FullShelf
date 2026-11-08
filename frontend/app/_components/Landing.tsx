import Image from "next/image";
import LandingHeader from "./LandingHeader";
import { CONTACT_PHONE_DISPLAY, CONTACT_TEL_URL, CONTACT_WHATSAPP_URL, SITE_URL } from "@/lib/site";

// Every picture on this page is a slot: /landing/<slot>.png in public/.
// `npx tsx scripts/screenshots.ts` fills them from the seeded local app (see
// scripts/); to use a different picture, overwrite the file with one of the
// same shape. Aspect ratios are fixed here, so a swapped-in image can't
// shift the layout.
type Slot = "counter" | "day-close" | "stock" | "low-stock" | "ledger";

const LAPTOP_RATIO = "aspect-[16/10]"; // screenshots are taken at 1440x900

function Shot({
  slot,
  alt,
  ratio,
  priority = false,
  className = "",
}: {
  slot: Slot;
  alt: string;
  ratio: string;
  priority?: boolean;
  className?: string;
}) {
  return (
    // Plain <img>: these are static screenshots that get swapped by file name.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      data-slot={slot}
      src={`/landing/${slot}.png`}
      alt={alt}
      loading={priority ? "eager" : "lazy"}
      className={`w-full rounded-lg border border-ink/20 object-cover object-top ${ratio} ${className}`}
    />
  );
}

const PROBLEM = [
  "The notebook says one thing. The shelf says another.",
  "The drawer is short and nobody can tell you why.",
  "You're not in the shop, so you find out when it's too late.",
];

const STEPS = [
  { title: "Staff key each sale at the counter.", body: "Tap the product, enter the weight or count, confirm. It takes seconds." },
  { title: "Stock and cash update as they go.", body: "You don't count anything. Every sale moves both numbers straight away." },
  { title: "The day closes with one number you can check.", body: "It's the cash that should be in the drawer. Count the drawer against it." },
];

const DEVICES = ["Your phone", "A laptop", "A desktop computer", "The POS machine already on your counter"];

const FEATURES: { slot: Slot; title: string; body: string; alt: string }[] = [
  {
    slot: "day-close",
    title: "Know the cash to expect, wherever you are",
    body: "Sales, discounts and the cash that should be in the drawer, for today or any day you pick. Open it on your phone from home, from church, from another town.",
    alt: "The owner's day-close screen: total sales, discounts and expected cash for a chosen day, with every sale listed.",
  },
  {
    slot: "stock",
    title: "See what's left without counting the shelf",
    body: "Every product, how much is left, and the level where you want a warning. Anything that has gone below zero is flagged so you can ask why.",
    alt: "The owner's stock screen: each product with its current quantity and alert level, products that need attention at the top.",
  },
  {
    slot: "low-stock",
    title: "Get a text before you run out",
    body: "When a product drops below the level you set, I send you one text message (SMS). It works on any phone, and you get it once, not every time someone buys. The same list is on your dashboard.",
    alt: "The owner's dashboard, listing the products that have dropped below their alert level.",
  },
  {
    slot: "ledger",
    title: "Fix a wrong entry and keep the trail",
    body: "If a sale is keyed wrong, you cancel it and staff key it again. The wrong one stays on record, marked as cancelled, so you can always see what happened and when.",
    alt: "A cancelled receipt in the owner's receipts, still visible and marked as voided.",
  },
];

const OBJECTIONS = [
  {
    q: "What if my staff key a sale wrong?",
    a: "They can't edit or delete anything. You cancel the wrong sale from your own login: the stock goes back, the expected cash comes down, and the record stays visible. Staff then key it correctly.",
  },
  {
    q: "What if a sale never gets keyed?",
    a: "Then FullShelf can't see it. It counts what is keyed, so the rule has to be that every sale is keyed before the goods are packed. What it does show you is discounts by person, short deliveries, and cash that doesn't match the keyed sales.",
  },
  {
    q: "What if the internet drops?",
    a: "Selling needs a connection. FullShelf doesn't work offline yet. For those minutes, write the sales down and key them once it's back.",
  },
  {
    q: "What happens to my old stock card?",
    a: "Once, on day one, you count the shop and that count is the starting figure. I key your products in with you. From then on FullShelf keeps the count as things come in and go out. Keep the card as a backup if you like, but you won't need to update it.",
  },
  {
    q: "Can my staff see my stock and cash?",
    a: "No. Staff get the sales screen and today's receipts, each with its own total. They can't see your stock levels, your dashboard or the day's cash. They each have their own PIN, and it only works on the shop's own device.",
  },
];

// Describes the product to a crawler as well as a visitor. Only claims what
// exists today: no printing, WhatsApp alerts or mobile money.
const DESCRIPTION =
  "Stock, sales and expected cash for grocery shops and mini-marts in Ghana. Staff key each sale; the owner sees stock, sales and the cash to expect from her phone.";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "FullShelf",
  url: SITE_URL,
  applicationCategory: "BusinessApplication",
  operatingSystem: "Any (web browser)",
  description: DESCRIPTION,
};

function CtaButtons({ hero = false }: { hero?: boolean }) {
  return (
    <div>
      <div className={`flex flex-wrap gap-3 ${hero ? "justify-center" : ""}`}>
        <a
          href={CONTACT_WHATSAPP_URL}
          className={`rounded px-5 py-3 font-medium ${hero ? "bg-on-dark text-primary" : "bg-primary text-on-dark"}`}
        >
          Set up my shop
        </a>
        <a
          href="#how"
          className={`rounded border px-5 py-3 font-medium ${hero ? "border-on-dark text-on-dark" : "border-secondary text-secondary"}`}
        >
          See how it works
        </a>
      </div>
      <p className="mt-3 text-sm">
        Chat on WhatsApp, or call me on{" "}
        <a href={CONTACT_TEL_URL} className="font-medium underline">
          {CONTACT_PHONE_DISPLAY}
        </a>
        .
      </p>
    </div>
  );
}

export default function Landing() {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD).replace(/</g, "\\u003c") }}
      />

      <LandingHeader />

      <main>
        {/* 1. Hero: full-bleed photo, copy centred over a top-down scrim */}
        <section className="relative isolate flex min-h-[30rem] justify-center overflow-hidden md:min-h-[85vh]">
          <Image
            src="/landing/hero-banner.jpeg"
            alt="A tablet and a phone showing the FullShelf sales screen on a shop counter."
            fill
            priority
            sizes="100vw"
            className="-z-20 object-cover object-[46%_100%]"
          />
          <div aria-hidden className="absolute inset-0 -z-10 bg-gradient-to-b from-ink/85 via-ink/70 via-70% to-transparent" />
          <div className="mx-auto max-w-4xl px-4 pb-24 pt-24 text-center text-on-dark md:pb-32 md:pt-32">
            <p className="text-xs font-semibold uppercase tracking-[0.2em]">FullShelf</p>
            <h1 className="mt-4 text-4xl font-bold sm:text-5xl">
              Know what&apos;s on the shelf, what sold, and what cash to expect.
            </h1>
            <p className="mx-auto mt-4 max-w-xl text-lg text-on-dark/90">
              Your staff key each sale. You see stock and cash from your phone.
            </p>
            <div className="mt-8">
              <CtaButtons hero />
            </div>
          </div>
        </section>

        {/* 2. The problem */}
        <section className="bg-on-dark">
          <div className="mx-auto max-w-5xl px-4 py-12">
            <h2 className="sr-only">The problem</h2>
            <ul className="grid gap-6 md:grid-cols-3">
              {PROBLEM.map((line) => (
                <li key={line} className="border-l-4 border-secondary pl-4 text-xl font-semibold">
                  {line}
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* 3. How it works */}
        <section id="how" className="mx-auto max-w-5xl scroll-mt-4 px-4 py-12">
          <h2 className="text-2xl font-semibold text-primary">Three steps, and you&apos;re not counting any of them</h2>
          <ol className="mt-6 grid gap-4 md:grid-cols-3">
            {STEPS.map((s, i) => (
              <li key={s.title} className="rounded-lg border border-ink/20 p-5">
                <span className="text-sm font-semibold text-secondary">Step {i + 1}</span>
                <h3 className="mt-1 font-semibold">{s.title}</h3>
                <p className="mt-2 text-sm">{s.body}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* 4. Works on what you already have */}
        <section className="mx-auto grid max-w-5xl items-center gap-8 px-4 pb-12 md:grid-cols-2">
          <div>
            <h2 className="text-2xl font-semibold text-primary">Works on what you already have</h2>
            <p className="mt-3">
              Nothing to buy and nothing to install. FullShelf runs in the browser on anything that can get online:
            </p>
            <ul className="mt-4 space-y-2">
              {DEVICES.map((d) => (
                <li key={d} className="flex gap-2">
                  <span aria-hidden className="text-primary">✓</span>
                  {d}
                </li>
              ))}
            </ul>
            <p className="mt-4 text-sm text-ink/70">
              Staff use the shop&apos;s own device. You use your phone, wherever you are.
            </p>
          </div>
          <Shot
            slot="counter"
            alt="The sales screen open in a browser on a counter laptop, with the order panel beside the product tiles."
            ratio={LAPTOP_RATIO}
          />
        </section>

        {/* 5. Proof */}
        <section className="bg-on-dark">
          <div className="mx-auto max-w-5xl border-l-4 border-primary px-4 py-10 md:px-6">
            <h2 className="text-2xl font-semibold text-primary">Built in a real Accra shop, used every day</h2>
            <p className="mt-3 max-w-2xl text-lg">
              FullShelf was made with a grocery shop in Accra. Their staff have keyed every sale on it since
              November 2026.
            </p>
          </div>
        </section>

        {/* 6. Features, as outcomes */}
        <section className="mx-auto max-w-5xl px-4 py-12">
          <h2 className="text-2xl font-semibold text-primary">What you get</h2>
          <div className="mt-8 space-y-12">
            {FEATURES.map((f, i) => (
              <article key={f.slot} className="grid items-center gap-6 md:grid-cols-2">
                <div className={i % 2 === 1 ? "md:order-2" : ""}>
                  <h3 className="text-xl font-semibold text-secondary">{f.title}</h3>
                  <p className="mt-2">{f.body}</p>
                </div>
                <Shot slot={f.slot} alt={f.alt} ratio={LAPTOP_RATIO} />
              </article>
            ))}
          </div>
        </section>

        {/* 7. Pricing */}
        <section className="bg-on-dark">
          <div className="mx-auto max-w-5xl px-4 py-12">
            <h2 className="text-2xl font-semibold text-primary">What it costs</h2>
            <p className="mt-2">Cash. No contract to sign, no equipment to buy.</p>
            <div className="mt-6 grid gap-4 md:grid-cols-2">
              <div className="rounded-lg border border-ink/20 bg-bg p-5">
                <h3 className="font-semibold text-secondary">Setup, once</h3>
                <p className="mt-1 text-3xl font-semibold text-primary">GH₵5,000</p>
                <ul className="mt-3 space-y-1 text-sm">
                  <li>I activate your shop on FullShelf.</li>
                  <li>I train you and your staff.</li>
                  <li>I key your products in with you.</li>
                </ul>
              </div>
              <div className="rounded-lg border border-ink/20 bg-bg p-5">
                <h3 className="font-semibold text-secondary">Keeping it running</h3>
                <p className="mt-1 text-3xl font-semibold text-primary">
                  GH₵5,100<span className="text-base font-normal"> a year</span>
                </p>
                <p className="mt-1 text-sm">
                  or GH₵500 a month. Paying for the year saves you GH₵900.
                </p>
                <p className="mt-3 text-sm">Covers hosting, your low-stock text messages and fixes.</p>
              </div>
            </div>
          </div>
        </section>

        {/* 8. Objections */}
        <section className="mx-auto max-w-3xl px-4 py-12">
          <h2 className="text-2xl font-semibold text-primary">Questions I get asked</h2>
          <dl className="mt-6 space-y-6">
            {OBJECTIONS.map((o) => (
              <div key={o.q}>
                <dt className="font-semibold">{o.q}</dt>
                <dd className="mt-1">{o.a}</dd>
              </div>
            ))}
          </dl>
        </section>

        {/* 9. Closing CTA */}
        <section className="border-t border-ink/10">
          <div className="mx-auto max-w-5xl px-4 py-12">
            <h2 className="text-2xl font-semibold text-primary">Know tonight&apos;s cash before you close.</h2>
            <p className="mt-2 max-w-xl">
              Message me and I&apos;ll set up your shop, train your staff and key in your products.
            </p>
            <div className="mt-6">
              <CtaButtons />
            </div>
          </div>
        </section>
      </main>

      <footer className="px-4 py-6 text-center text-sm text-ink/70">
        <p>FullShelf · Made in Ghana · Prices in GH₵</p>
      </footer>
    </div>
  );
}
