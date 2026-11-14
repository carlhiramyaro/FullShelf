import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import StatTile from "../_components/StatTile";

type Report = {
  from: string;
  to: string;
  netSales: number;
  discounts: number;
  salesCash: number;
  salesMomo: number;
  expectedCash: number;
  expectedMomo: number;
  expensesCash: number;
  expensesMomo: number;
  costedRevenue: number;
  costOfGoods: number;
  grossProfit: number;
  marginPercent: number | null;
  writeOffCost: number;
  writeOffsWithoutCost: number;
  expensesTotal: number;
  expensesByCategory: { category: string; label: string; amount: number }[];
  netProfit: number;
  totalLines: number;
  costedLines: number;
  estimatedLines: number;
  topProducts: { productId: number; name: string; revenue: number; cost: number; profit: number }[];
  stockValue: number;
  productsWithoutCost: number;
};

async function fetchReport(from: string, to: string): Promise<Report | { error: string }> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/reports?from=${from}&to=${to}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (res.status === 400) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That range isn't valid." };
  }
  if (!res.ok) {
    throw new Error(`Failed to load report: ${res.status}`);
  }
  return res.json();
}

// Same "server clock = Accra" simplification as everywhere (UTC, no conversion).
function iso(d: Date) {
  return d.toISOString().slice(0, 10);
}

function presets() {
  const now = new Date();
  const today = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  // Weeks run Monday to Sunday; "this week" ends today.
  const sinceMonday = (today.getUTCDay() + 6) % 7;
  const monday = new Date(today);
  monday.setUTCDate(today.getUTCDate() - sinceMonday);
  const monthStart = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1));
  const lastMonthStart = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() - 1, 1));
  const lastMonthEnd = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 0));
  return [
    { label: "Today", from: iso(today), to: iso(today) },
    { label: "This week", from: iso(monday), to: iso(today) },
    { label: "This month", from: iso(monthStart), to: iso(today) },
    { label: "Last month", from: iso(lastMonthStart), to: iso(lastMonthEnd) },
  ];
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex items-center justify-between py-2 text-sm ${bold ? "font-semibold text-ink" : "text-ink/80"}`}>
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}

// finance-scope.md section 4. Profit is worked out only over sale lines that have a cost, and the
// coverage line says how many that is, so the owner can see how far to trust the figure.
export default async function ReportsPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const params = await searchParams;
  const defaults = presets()[2]; // month to date
  const from = params.from ?? defaults.from;
  const to = params.to ?? defaults.to;
  const result = await fetchReport(from, to);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">Reports</h1>

      <div className="mb-4 flex flex-wrap gap-2">
        {presets().map((p) => (
          <Link
            key={p.label}
            href={`/owner/reports?from=${p.from}&to=${p.to}`}
            className={`rounded-lg border px-3 py-2 text-sm font-medium ${
              p.from === from && p.to === to
                ? "border-primary bg-primary/10 text-primary"
                : "border-ink/20 text-secondary hover:text-primary"
            }`}
          >
            {p.label}
          </Link>
        ))}
      </div>

      <form className="mb-8 flex flex-wrap items-end gap-3">
        <label className="flex flex-col text-sm text-ink/70">
          From
          <input type="date" name="from" defaultValue={from} className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          To
          <input type="date" name="to" defaultValue={to} className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink" />
        </label>
        <button type="submit" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-dark">
          View
        </button>
      </form>

      {"error" in result ? (
        <p className="text-sm font-medium text-error">⚠ {result.error}</p>
      ) : (
        <ReportBody report={result} />
      )}
    </div>
  );
}

function ReportBody({ report }: { report: Report }) {
  const uncosted = report.totalLines - report.costedLines;
  const loss = report.netProfit < 0;
  return (
    <>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatTile
          label="Net sales"
          value={formatCash(report.netSales)}
          hint={`After ${formatCash(report.discounts)} discounts`}
        />
        <StatTile
          label="Gross profit"
          value={formatCash(report.grossProfit)}
          hint={
            report.marginPercent === null
              ? "No costed sales in this range"
              : `${report.marginPercent.toFixed(1)}% margin`
          }
          tone={report.grossProfit < 0 ? "error" : undefined}
        />
        <StatTile
          label="Net profit"
          value={formatCash(report.netProfit)}
          hint={loss ? "⚠ Loss · after write-offs and expenses" : "After write-offs and expenses"}
          tone={loss ? "error" : undefined}
        />
        <StatTile
          label="Stock value on hand (now)"
          value={formatCash(report.stockValue)}
          hint={
            report.productsWithoutCost > 0
              ? `${report.productsWithoutCost} ${report.productsWithoutCost === 1 ? "product has" : "products have"} no cost set`
              : undefined
          }
        />
      </div>

      <p className="mb-8 text-sm text-ink">
        {report.totalLines === 0
          ? "No sales in this range."
          : `Profit counts ${report.costedLines} of ${report.totalLines} sale lines; ${report.estimatedLines} ${
              report.estimatedLines === 1 ? "is" : "are"
            } estimated from today's cost.`}
        {uncosted > 0 && (
          <span className="font-medium text-alert-text">
            {" "}
            ⚠ {uncosted} {uncosted === 1 ? "line has" : "lines have"} no cost, so {uncosted === 1 ? "it is" : "they are"} left out of profit.
          </span>
        )}
      </p>

      <h2 className="mb-1 text-sm font-medium text-ink/70">How net profit is worked out</h2>
      <div className="mb-8 divide-y divide-ink/10 rounded-lg border border-ink/10 px-4">
        <Row label="Sales with a cost" value={formatCash(report.costedRevenue)} />
        <Row label="− Cost of goods sold" value={formatCash(report.costOfGoods)} />
        <Row label="= Gross profit" value={formatCash(report.grossProfit)} bold />
        <Row label="− Write-off cost" value={formatCash(report.writeOffCost)} />
        <Row label="− Expenses" value={formatCash(report.expensesTotal)} />
        <Row label="= Net profit" value={formatCash(report.netProfit)} bold />
      </div>
      {report.writeOffsWithoutCost > 0 && (
        <p className="-mt-6 mb-8 text-sm font-medium text-alert-text">
          ⚠ {report.writeOffsWithoutCost} {report.writeOffsWithoutCost === 1 ? "write-off has" : "write-offs have"} no cost and
          {report.writeOffsWithoutCost === 1 ? " isn't" : " aren't"} valued.
        </p>
      )}

      <h2 className="mb-1 text-sm font-medium text-ink/70">Sales by payment method</h2>
      <div className="mb-8 divide-y divide-ink/10 rounded-lg border border-ink/10 px-4">
        <Row label="Cash sales" value={formatCash(report.salesCash)} />
        <Row label="MoMo sales" value={formatCash(report.salesMomo)} />
        <Row label="Expected cash (cash sales − cash expenses)" value={formatCash(report.expectedCash)} bold />
        <Row label="Expected MoMo (MoMo sales − MoMo expenses)" value={formatCash(report.expectedMomo)} bold />
      </div>

      <h2 className="mb-1 text-sm font-medium text-ink/70">Expenses by category</h2>
      {report.expensesByCategory.length === 0 ? (
        <p className="mb-8 text-sm text-ink/50">No expenses in this range.</p>
      ) : (
        <div className="mb-8 divide-y divide-ink/10 rounded-lg border border-ink/10 px-4">
          {report.expensesByCategory.map((c) => (
            <Row key={c.category} label={c.label} value={formatCash(c.amount)} />
          ))}
        </div>
      )}

      <h2 className="mb-1 text-sm font-medium text-ink/70">Top products by profit</h2>
      {report.topProducts.length === 0 ? (
        <p className="text-sm text-ink/50">No costed sales in this range.</p>
      ) : (
        <ol className="flex flex-col gap-2">
          {report.topProducts.map((p, i) => (
            <li key={p.productId} className="flex items-center justify-between rounded-lg border border-ink/10 p-3">
              <span>
                <span className="mr-2 text-xs text-ink/50">{i + 1}.</span>
                <span className="font-medium text-ink">{p.name}</span>
                <span className="ml-2 text-xs text-ink/50">sold {formatCash(p.revenue)}</span>
              </span>
              <span className="font-medium text-primary">{formatCash(p.profit)}</span>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
