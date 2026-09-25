import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";

type SaleSummary = {
  receiptNumber: number;
  createdAt: string;
  staffName: string;
  total: number;
  voided: boolean;
};

async function fetchSales(): Promise<SaleSummary[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/sales`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load sales: ${res.status}`);
  }
  return res.json();
}

// "Owner sees all" (mvp.md), deliberately unfiltered by date — picking a
// date to see totals is Day close's job (Phase F), not this screen's.
export default async function OwnerSalesPage() {
  const sales = await fetchSales();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Sales</h1>
      <p className="mb-6 text-sm text-ink/60">Every confirmed sale, newest first.</p>

      {sales.length === 0 ? (
        <p className="text-sm text-ink/50">No sales yet.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {sales.map((sale) => (
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
                <span className="font-medium text-ink">GH₵{sale.total.toFixed(2)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
