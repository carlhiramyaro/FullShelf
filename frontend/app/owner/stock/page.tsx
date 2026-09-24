import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { setOpeningStockAction } from "./actions";

type ProductOpeningStatus = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  alreadySet: boolean;
  currentBalance: number;
};

async function fetchOpeningStock(): Promise<ProductOpeningStatus[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/opening`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load opening stock: ${res.status}`);
  }
  return res.json();
}

export default async function OpeningStockPage() {
  const products = await fetchOpeningStock();
  const unset = products.filter((product) => !product.alreadySet);
  const set = products.filter((product) => product.alreadySet);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Opening stock</h1>
      <p className="mb-6 text-sm text-ink/60">
        One-time starting balance per product, keyed from a real count on the day. Fill in
        whichever products you&apos;ve counted so far — the rest can wait until next time.
      </p>

      {unset.length > 0 && (
        <form
          action={setOpeningStockAction}
          className="mb-8 flex flex-col gap-3 rounded-lg border border-ink/10 p-4"
        >
          {unset.map((product) => (
            <label
              key={product.productId}
              className="flex items-center justify-between gap-3 text-sm text-ink/70"
            >
              <span>
                {product.name}{" "}
                <span className="text-ink/40">({product.unit === "KG" ? "kg" : "units"})</span>
              </span>
              <input
                name={`qty-${product.productId}`}
                type="number"
                step="0.01"
                min="0"
                placeholder="not counted yet"
                className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
              />
            </label>
          ))}
          <button
            type="submit"
            className="mt-2 self-start rounded bg-primary px-4 py-1.5 text-sm text-on-dark"
          >
            Save opening stock
          </button>
        </form>
      )}

      {set.length > 0 && (
        <div className="rounded-lg border border-ink/10 p-4">
          <h2 className="mb-3 text-sm font-medium text-ink/70">Already set</h2>
          <ul className="flex flex-col gap-1 text-sm text-ink/60">
            {set.map((product) => (
              <li key={product.productId} className="flex justify-between">
                <span>{product.name}</span>
                <span>
                  {product.currentBalance} {product.unit === "KG" ? "kg" : "units"}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {products.length === 0 && <p className="text-sm text-ink/50">No active products yet.</p>}
    </div>
  );
}
