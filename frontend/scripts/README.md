# Landing page images: how they are made

The six pictures on the public landing page (`app/_components/Landing.tsx`) are
real screenshots of this app, taken from the **local** database after a seeded
trading day. Two scripts do it. Why it is built this way: `docs/decisions.md`,
"Landing page images: seeded day + Playwright".

## Run it

Needs the backend on `:8080`, the frontend on **`:3001`** (`:3000` is taken by
another project on this machine; the backend already allows the 3001 origin),
and the local Postgres.

```
cd frontend
npm i
npx playwright install chromium        # once
npm run seed:day -- --dry-run           # read-only: shows the plan
npm run seed:day                        # writes one trading day
npm run screenshots                     # writes public/landing/<slot>.png
```

- **Seed once per day.** It refuses to run if today (UTC) already has sales.
  `--force` adds a second day on top: avoid it, sales can only be voided, never
  deleted. To redo only the pictures, run `npm run screenshots` again. The seed
  isn't needed.
- Override addresses with `APP_BASE_URL` / `API_BASE_URL` (default
  `http://localhost:3001` / the `.env.local` API URL).
- Both scripts refuse to run unless the API and app are on localhost and
  `CLERK_SECRET_KEY` is a development key (`sk_test_`). Production holds a real
  ledger. Never point them at it.

## The six slots

| Slot        | View            | Shows                                              | Landing ratio |
| ----------- | --------------- | -------------------------------------------------- | ------------- |
| `hero`      | phone 390x844   | staff sales screen, the order open                 | 390:844       |
| `counter`   | laptop 1440x900 | staff sales screen, order beside the tiles         | 16:10         |
| `day-close` | laptop, cropped | owner's day close for today                        | 16:10         |
| `stock`     | laptop, cropped | owner's stock levels, needs-attention first        | 16:10         |
| `low-stock` | laptop, cropped | owner's dashboard low-stock list                   | 16:10         |
| `ledger`    | laptop, cropped | the voided receipt (record stays visible)          | 16:10         |

All at deviceScaleFactor 2. The owner pages are one narrow centred column, so
those four are cropped to the middle 960x600 (`OWNER_CROP` in
`screenshots.ts`), which keeps 16:10. The ratios are fixed in `Landing.tsx`
(`PHONE_RATIO`, `LAPTOP_RATIO`); change a shot's shape in both places or the
layout will crop it.

## What the seed creates (all through the real endpoints)

Two staff (Abena Owusu PIN 2468, Kofi Mensah PIN 1357), 62 sales (kg including
0.5 kg, units, 5 flat GH₵ discounts), 3-4 best sellers sold down below their
alert level, one write-off, and one mis-keyed sale (10 kg instead of 1 kg) that
the owner voids so the `ledger` shot has a cancelled receipt. The plan is seeded
from today's date. It tops up the catalogue only if it has fewer than 3 kg or 8
unit products. The shop device it pairs is revoked at the end.

## How the scripts get in

- **Owner:** a Clerk session JWT minted from the Backend API for
  `SEED_OWNER_EMAIL` (default carlhiramyaro@outlook.com); Playwright signs in
  with a one-time sign-in ticket. No password or code.
- **Staff:** a fresh device is paired with a real pairing code, then PIN login.

## Changing things

- **Different retake of a picture:** edit the order in `chooseOrder`, the page
  list in `main`, or `OWNER_CROP` in `screenshots.ts`, then `npm run screenshots`.
- **A different day's numbers:** the data is whatever today's seed made. A new
  look needs a new UTC day and a seed run, or swap a file by hand.
- **Use your own picture:** overwrite `public/landing/<slot>.png` with one of the
  same shape. The `low-stock` shot is the dashboard list, not an SMS; replace it
  with a real phone screenshot of the text if you want the headline proven.
- **Nothing is read from the files by the page except the name and the shape.**
  Alt text and headlines are in the `FEATURES` array in `Landing.tsx`.

## Gotchas already hit

- `localhost:3000` served a different app ("Ledger"), so the script found no
  FullShelf pages. Check `lsof -nP -iTCP:3001 -sTCP:LISTEN` first.
- The Next dev indicator showed bottom-left. `shot()` hides `<nextjs-portal>`.
- The images contain local names and totals. Look at them before committing.
- `public/landing/` and `scripts/` were not committed when this was written.
