import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";

type SaleLineDetail = {
  productId: number;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  discount: number;
  lineTotal: number;
};

type SaleDetail = {
  receiptNumber: number;
  createdAt: string;
  staffName: string;
  voided: boolean;
  total: number;
  lines: SaleLineDetail[];
};

async function fetchSale(receiptNumber: string): Promise<SaleDetail> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/sales/${receiptNumber}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load receipt: ${res.status}`);
  }
  return res.json();
}

export default async function OwnerSaleDetailPage({
  params,
}: {
  params: Promise<{ receiptNumber: string }>;
}) {
  const { receiptNumber } = await params;
  const sale = await fetchSale(receiptNumber);

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/owner/sales" className="mb-4 inline-block text-sm text-primary hover:underline">
        ← Back
      </Link>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-ink">Receipt #{sale.receiptNumber}</h1>
        {sale.voided && (
          <span className="rounded bg-error px-2 py-1 text-xs font-medium text-on-dark">Voided</span>
        )}
      </div>
      <p className="mb-6 text-sm text-ink/60">
        {sale.staffName} · {new Date(sale.createdAt).toLocaleString()}
      </p>

      <div className="mb-4 rounded-lg border border-ink/10 p-4">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-ink/50">
              <th className="pb-2 font-medium">Product</th>
              <th className="pb-2 font-medium">Qty</th>
              <th className="pb-2 font-medium">Price</th>
              <th className="pb-2 font-medium">Discount</th>
              <th className="pb-2 text-right font-medium">Line total</th>
            </tr>
          </thead>
          <tbody>
            {sale.lines.map((line) => (
              <tr key={line.productId} className="border-t border-ink/10">
                <td className="py-2 text-ink">{line.productName}</td>
                <td className="py-2 text-ink/70">
                  {line.quantity} {line.unit === "UNIT" ? "" : "kg"}
                </td>
                <td className="py-2 text-ink/70">GH₵{line.unitPrice.toFixed(2)}</td>
                <td className="py-2 text-ink/70">
                  {line.discount > 0 ? `GH₵${line.discount.toFixed(2)}` : "—"}
                </td>
                <td className="py-2 text-right text-ink">GH₵{line.lineTotal.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="text-right text-lg font-semibold text-ink">Total: GH₵{sale.total.toFixed(2)}</p>
    </div>
  );
}
