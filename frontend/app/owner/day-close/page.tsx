import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import StatTile from "../_components/StatTile";

type DaySale = {
  receiptNumber: number;
  createdAt: string;
  staffName: string;
  total: number;
  discount: number;
  voided: boolean;
};

type DayClose = {
  totalSales: number;
  totalDiscounts: number;
  expectedCash: number;
  sales: DaySale[];
};

async function fetchDayClose(date: string): Promise<DayClose> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/day-close/${date}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load day close: ${res.status}`);
  }
  return res.json();
}

// Same "server clock = Accra" simplification the backend already makes
// (Accra is UTC+0, no DST) — today's date here is just the server's own
// UTC calendar date, with no timezone conversion needed.
function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

// mvp.md's Day close: pick a date, see every sale for it with price,
// discount and staff name, plus totals. The date picker is a plain GET
// form (no client JS) — submitting it just re-requests this page with a
// new ?date=, which the Server Component below re-fetches.
export default async function DayClosePage({
  searchParams,
}: {
  searchParams: Promise<{ date?: string }>;
}) {
  const { date } = await searchParams;
  const pickedDate = date ?? todayIsoDate();
  const dayClose = await fetchDayClose(pickedDate);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">Day close</h1>

      <form className="mb-8 flex items-end gap-3">
        <label className="flex flex-col text-sm text-ink/70">
          Date
          <input
            type="date"
            name="date"
            defaultValue={pickedDate}
            className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink"
          />
        </label>
        <button
          type="submit"
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-dark"
        >
          View
        </button>
      </form>

      <div className="mb-8 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatTile label="Sales" value={formatCash(dayClose.totalSales)} />
        <StatTile label="Discounts" value={formatCash(dayClose.totalDiscounts)} />
        <StatTile label="Expected cash" value={formatCash(dayClose.expectedCash)} />
      </div>

      {dayClose.sales.length === 0 ? (
        <p className="text-sm text-ink/50">No sales on this date.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {dayClose.sales.map((sale) => (
            <li key={sale.receiptNumber}>
              <Link
                href={`/owner/sales/${sale.receiptNumber}`}
                className="flex items-center justify-between rounded-lg border border-ink/10 p-3 hover:bg-secondary/10"
              >
                <span>
                  <span className="font-medium text-ink">#{sale.receiptNumber}</span>{" "}
                  <span className="text-xs text-ink/50">
                    {sale.staffName} · {new Date(sale.createdAt).toLocaleString()}
                  </span>
                  {sale.voided && (
                    <span className="ml-2 text-xs font-medium text-error">Voided</span>
                  )}
                </span>
                <span className="text-right">
                  <span className="block font-medium text-ink">{formatCash(sale.total)}</span>
                  {sale.discount > 0 && (
                    <span className="block text-xs text-ink/50">
                      {formatCash(sale.discount)} discount
                    </span>
                  )}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
