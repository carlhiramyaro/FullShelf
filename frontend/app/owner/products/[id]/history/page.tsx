import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import StatTile from "../../../_components/StatTile";

type Row = {
  kind: "LEDGER" | "PRICE_CHANGE";
  id: number;
  at: string;
  type: string;
  quantity: number | null;
  balanceAfter: number | null;
  staffName: string | null;
  note: string | null;
  receiptNumber: number | null;
  reversedMovementId: number | null;
  price: number | null;
  costPrice: number | null;
  previousPrice: number | null;
  previousCost: number | null;
};

type History = {
  product: {
    id: number;
    name: string;
    unit: "KG" | "UNIT";
    price: number;
    costPrice: number | null;
    currentBalance: number;
  };
  from: string | null;
  to: string | null;
  type: string | null;
  page: number;
  pageSize: number;
  total: number;
  hasNext: boolean;
  summary: {
    received: number;
    sold: number;
    writtenOff: number;
    netChange: number;
    revenue: number;
    discounts: number;
    averagePrice: number | null;
    sales: number;
    grossProfit: number | null;
    marginPercent: number | null;
    saleLines: number;
    costedLines: number;
  };
  rows: Row[];
};

type Search = { from?: string; to?: string; type?: string; page?: string };

async function fetchHistory(id: string, search: Search): Promise<History | { error: string }> {
  const { getToken } = await auth();
  const token = await getToken();
  const qs = new URLSearchParams();
  if (search.from) qs.set("from", search.from);
  if (search.to) qs.set("to", search.to);
  if (search.type) qs.set("type", search.type);
  if (search.page) qs.set("page", search.page);
  const res = await fetch(`${API_BASE_URL}/api/owner/products/${id}/history?${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (res.status === 400) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That history isn't available." };
  }
  if (!res.ok) {
    throw new Error(`Failed to load product history: ${res.status}`);
  }
  return res.json();
}

const TYPE_LABELS: Record<string, string> = {
  OPENING: "Opening stock",
  RECEIVED: "Received",
  SALE: "Sold",
  WRITE_OFF: "Written off",
  VOID: "Void",
  REVERSAL: "Reversal",
  COUNT_ADJUST: "Count adjustment",
  PRICE_CHANGE: "Price or cost change",
};

// Same "server clock = Accra" simplification as everywhere: UTC, no conversion.
function formatTime(iso: string) {
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)}`;
}

function money(value: number | null) {
  return value === null ? "no cost" : formatCash(value);
}

function qty(value: number, unit: string) {
  return `${value} ${unit}`;
}

function changed(before: number | null, after: number | null) {
  return (before === null) !== (after === null) || (before !== null && after !== null && before !== after);
}

function PriceChangeText({ row }: { row: Row }) {
  if (row.previousPrice === null) {
    return (
      <>
        Starting point: price {formatCash(row.price ?? 0)}, cost {money(row.costPrice)}
      </>
    );
  }
  return (
    <>
      {changed(row.previousPrice, row.price) && (
        <span className="block">
          Price {formatCash(row.previousPrice)} → {formatCash(row.price ?? 0)}
        </span>
      )}
      {changed(row.previousCost, row.costPrice) && (
        <span className="block">
          Cost {money(row.previousCost)} → {money(row.costPrice)}
        </span>
      )}
    </>
  );
}

function HistoryRow({ row, unit }: { row: Row; unit: string }) {
  const isPrice = row.kind === "PRICE_CHANGE";
  // Void is "something's wrong", so red, but always with its label and a ⚠, never colour alone.
  const isVoid = row.type === "VOID";
  return (
    <li className="rounded-lg border border-ink/10 p-3">
      <div className="flex items-start justify-between gap-3">
        <span>
          <span className={`font-medium ${isVoid ? "text-error" : "text-ink"}`}>
            {isVoid && "⚠ "}
            {TYPE_LABELS[row.type] ?? row.type}
          </span>
          {row.receiptNumber !== null && (
            <Link
              href={`/owner/sales/${row.receiptNumber}`}
              className="ml-2 text-sm text-secondary underline hover:text-primary"
            >
              Receipt #{row.receiptNumber}
            </Link>
          )}
          {row.reversedMovementId !== null && row.receiptNumber === null && (
            <span className="ml-2 text-xs text-ink/60">undoes entry #{row.reversedMovementId}</span>
          )}
        </span>
        {!isPrice && row.quantity !== null && (
          <span className="whitespace-nowrap text-sm font-medium text-ink">
            {row.quantity > 0 ? "+" : ""}
            {qty(row.quantity, unit)}
          </span>
        )}
      </div>
      {isPrice && (
        <p className="mt-1 text-sm text-ink/80">
          <PriceChangeText row={row} />
        </p>
      )}
      {row.note && <p className="mt-1 text-sm text-ink/70">{row.note}</p>}
      <div className="mt-1 flex flex-wrap items-center justify-between gap-2 text-xs text-ink/60">
        <span>
          {formatTime(row.at)} UTC{row.staffName ? ` · ${row.staffName}` : ""}
        </span>
        {!isPrice && row.balanceAfter !== null && (
          <span className={row.balanceAfter < 0 ? "font-medium text-error" : undefined}>
            {row.balanceAfter < 0 && "⚠ Below zero · "}
            Balance after: {qty(row.balanceAfter, unit)}
          </span>
        )}
      </div>
    </li>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2 text-sm text-ink/80">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}

// finance-scope.md section 5. Read-only: corrections still go through Reverse entry and Void.
export default async function ProductHistoryPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<Search>;
}) {
  const { id } = await params;
  const search = await searchParams;
  const result = await fetchHistory(id, search);

  if ("error" in result) {
    return (
      <div className="mx-auto max-w-2xl">
        <Link href="/owner/products" className="text-sm text-secondary underline">
          ← Products
        </Link>
        <p className="mt-4 text-sm font-medium text-error">⚠ {result.error}</p>
      </div>
    );
  }

  const { product, summary } = result;
  const unit = product.unit === "KG" ? "kg" : "units";
  const base = `/owner/products/${product.id}/history`;
  const pageLink = (page: number) => {
    const qs = new URLSearchParams();
    if (search.from) qs.set("from", search.from);
    if (search.to) qs.set("to", search.to);
    if (search.type) qs.set("type", search.type);
    qs.set("page", String(page));
    return `${base}?${qs}`;
  };
  const rangeLabel =
    search.from || search.to ? `${search.from ?? "the start"} to ${search.to ?? "today"}` : "all time";

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/owner/products" className="text-sm text-secondary underline">
        ← Products
      </Link>
      <h1 className="mb-1 mt-2 text-2xl font-semibold text-ink">{product.name}</h1>
      <p className="mb-6 text-sm text-ink/60">
        On hand {qty(product.currentBalance, unit)} · price {formatCash(product.price)} · cost {money(product.costPrice)}
      </p>

      <form className="mb-8 flex flex-wrap items-end gap-3">
        <label className="flex flex-col text-sm text-ink/70">
          From
          <input type="date" name="from" defaultValue={search.from ?? ""} className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          To
          <input type="date" name="to" defaultValue={search.to ?? ""} className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          Show
          <select name="type" defaultValue={search.type ?? ""} className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink">
            <option value="">Everything</option>
            {Object.entries(TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-dark">
          View
        </button>
      </form>

      <h2 className="mb-2 text-sm font-medium text-ink/70">Summary ({rangeLabel})</h2>
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatTile
          label="Sold"
          value={qty(summary.sold, unit)}
          hint={`${summary.sales} ${summary.sales === 1 ? "sale" : "sales"} · ${formatCash(summary.revenue)} after ${formatCash(summary.discounts)} discounts`}
        />
        <StatTile
          label="Gross profit"
          value={summary.grossProfit === null ? "–" : formatCash(summary.grossProfit)}
          hint={
            summary.grossProfit === null
              ? "No sale in this range has a cost"
              : `${summary.marginPercent?.toFixed(1)}% margin · ${summary.costedLines} of ${summary.saleLines} sale lines have a cost`
          }
          tone={summary.grossProfit !== null && summary.grossProfit < 0 ? "error" : undefined}
        />
      </div>
      <div className="mb-8 divide-y divide-ink/10 rounded-lg border border-ink/10 px-4">
        <Row label="Received" value={qty(summary.received, unit)} />
        <Row label="Sold (voided sales not counted)" value={qty(summary.sold, unit)} />
        <Row label="Written off" value={qty(summary.writtenOff, unit)} />
        <Row label="Net change in stock" value={`${summary.netChange > 0 ? "+" : ""}${qty(summary.netChange, unit)}`} />
        <Row
          label="Average selling price"
          value={summary.averagePrice === null ? "–" : `${formatCash(summary.averagePrice)} per ${product.unit === "KG" ? "kg" : "unit"}`}
        />
      </div>

      <h2 className="mb-2 text-sm font-medium text-ink/70">
        Timeline · {result.total} {result.total === 1 ? "entry" : "entries"}, newest first
      </h2>
      {result.rows.length === 0 ? (
        <p className="text-sm text-ink/50">Nothing matches these filters.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {result.rows.map((row) => (
            <HistoryRow key={`${row.kind}-${row.id}`} row={row} unit={unit} />
          ))}
        </ul>
      )}

      {(result.page > 0 || result.hasNext) && (
        <nav className="mt-6 flex items-center justify-between text-sm">
          {result.page > 0 ? (
            <Link href={pageLink(result.page - 1)} className="font-medium text-secondary underline">
              ← Newer
            </Link>
          ) : (
            <span />
          )}
          <span className="text-ink/60">Page {result.page + 1}</span>
          {result.hasNext ? (
            <Link href={pageLink(result.page + 1)} className="font-medium text-secondary underline">
              Older →
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}
    </div>
  );
}
