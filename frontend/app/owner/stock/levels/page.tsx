import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import { ProductSearch, SearchMatch } from "../../_components/ProductSearch";
import StatTile from "../../_components/StatTile";

type StockLevel = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  currentBalance: number;
  alertLevel: number;
  status: "NEGATIVE" | "LOW" | "OK";
};

async function fetchStockLevels(): Promise<StockLevel[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/levels`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load stock levels: ${res.status}`);
  }
  return res.json();
}

type StockValue = { stockValue: number; productsWithoutCost: number };

async function fetchStockValue(): Promise<StockValue> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/levels/value`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load stock value: ${res.status}`);
  }
  return res.json();
}

function unitLabel(unit: "KG" | "UNIT") {
  return unit === "KG" ? "kg" : "units";
}

function StatusBadge({ status }: { status: StockLevel["status"] }) {
  if (status === "NEGATIVE") {
    return (
      <span className="rounded bg-error px-2 py-1 text-xs font-medium text-on-dark">Below zero</span>
    );
  }
  if (status === "LOW") {
    return <span className="rounded bg-alert px-2 py-1 text-xs font-medium text-on-dark">Low stock</span>;
  }
  return <span className="text-xs font-medium text-primary">In stock</span>;
}

function StockRow({ level }: { level: StockLevel }) {
  return (
    <SearchMatch
      name={level.name}
      as="li"
      className="flex items-center justify-between rounded-lg border border-ink/10 p-3"
    >
      <span>
        <span className="font-medium text-ink">{level.name}</span>{" "}
        <span className="text-xs text-ink/50">
          alert at {level.alertLevel} {unitLabel(level.unit)}
        </span>
      </span>
      <span className="flex items-center gap-3">
        <span className="text-sm text-ink/70">
          {level.currentBalance} {unitLabel(level.unit)}
        </span>
        <StatusBadge status={level.status} />
      </span>
    </SearchMatch>
  );
}

// "Stock per system" (mvp.md) — every active product's current balance
// against its alert level, derived from the ledger. Products needing
// attention (negative or low) surface first so they're visible without
// scrolling past normal-stock rows on a phone.
export default async function StockLevelsPage() {
  const [levels, value] = await Promise.all([fetchStockLevels(), fetchStockValue()]);
  const needsAttention = levels.filter((level) => level.status !== "OK");
  const inStock = levels.filter((level) => level.status === "OK");

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Stock per system</h1>
      <p className="mb-6 text-sm text-ink/60">
        Current balance for every active product, derived from the ledger.
      </p>

      <div className="mb-6">
        <StatTile
          label="Stock value on hand"
          value={formatCash(value.stockValue)}
          hint={
            value.productsWithoutCost > 0
              ? `${value.productsWithoutCost} ${value.productsWithoutCost === 1 ? "product has" : "products have"} no cost set and ${value.productsWithoutCost === 1 ? "is" : "are"} left out. Set costs on Products.`
              : "At cost, counting negative stock as zero."
          }
        />
      </div>

      <ProductSearch>
      {needsAttention.length > 0 && (
        <div className="mb-6">
          <h2 className="mb-3 text-sm font-medium text-ink/70">Needs attention</h2>
          <ul className="flex flex-col gap-2">
            {needsAttention.map((level) => (
              <StockRow key={level.productId} level={level} />
            ))}
          </ul>
        </div>
      )}

      {inStock.length > 0 && (
        <div>
          <h2 className="mb-3 text-sm font-medium text-ink/70">In stock</h2>
          <ul className="flex flex-col gap-2">
            {inStock.map((level) => (
              <StockRow key={level.productId} level={level} />
            ))}
          </ul>
        </div>
      )}
      </ProductSearch>

      {levels.length === 0 && <p className="text-sm text-ink/50">No active products yet.</p>}
    </div>
  );
}
