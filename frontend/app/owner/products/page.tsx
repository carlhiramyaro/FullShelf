import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { createProductAction, deactivateProductAction, updateProductAction } from "./actions";

type Product = {
  id: number;
  name: string;
  unit: "KG" | "UNIT";
  price: number;
  alertLevel: number;
  cartonWeight: number | null;
  active: boolean;
};

async function fetchProducts(): Promise<Product[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/products`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load products: ${res.status}`);
  }
  return res.json();
}

export default async function ProductsPage() {
  const products = await fetchProducts();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">Products</h1>

      <form
        action={createProductAction}
        className="mb-8 flex flex-wrap items-end gap-3 rounded-lg border border-ink/10 p-4"
      >
        <label className="flex flex-col text-sm text-ink/70">
          Name
          <input name="name" required className="rounded border border-ink/20 bg-bg px-2 py-1" />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          Unit
          <select name="unit" required defaultValue="KG" className="rounded border border-ink/20 bg-bg px-2 py-1">
            <option value="KG">kg</option>
            <option value="UNIT">unit</option>
          </select>
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          Price (GH₵)
          <input
            name="price"
            type="number"
            step="0.01"
            min="0.01"
            required
            className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
          />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          Alert level
          <input
            name="alertLevel"
            type="number"
            step="0.01"
            min="0"
            required
            className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
          />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          Carton weight
          <input
            name="cartonWeight"
            type="number"
            step="0.01"
            min="0.01"
            className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
          />
        </label>
        <button type="submit" className="rounded bg-primary px-4 py-1.5 text-sm text-on-dark">
          Add product
        </button>
      </form>

      <ul className="flex flex-col gap-3">
        {products.map((product) => (
          <li key={product.id} className="rounded-lg border border-ink/10 p-4">
            <form action={updateProductAction} className="flex flex-wrap items-end gap-3">
              <input type="hidden" name="productId" value={product.id} />
              <label className="flex flex-col text-sm text-ink/70">
                Name
                <input
                  name="name"
                  defaultValue={product.name}
                  required
                  className="rounded border border-ink/20 bg-bg px-2 py-1"
                />
                {!product.active && (
                  <span className="mt-1 text-xs text-ink/50">deactivated</span>
                )}
              </label>
              <label className="flex flex-col text-sm text-ink/70">
                Unit
                <select
                  name="unit"
                  defaultValue={product.unit}
                  required
                  className="rounded border border-ink/20 bg-bg px-2 py-1"
                >
                  <option value="KG">kg</option>
                  <option value="UNIT">unit</option>
                </select>
              </label>
              <label className="flex flex-col text-sm text-ink/70">
                Price (GH₵)
                <input
                  name="price"
                  type="number"
                  step="0.01"
                  min="0.01"
                  defaultValue={product.price}
                  required
                  className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
                />
              </label>
              <label className="flex flex-col text-sm text-ink/70">
                Alert level
                <input
                  name="alertLevel"
                  type="number"
                  step="0.01"
                  min="0"
                  defaultValue={product.alertLevel}
                  required
                  className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
                />
              </label>
              <label className="flex flex-col text-sm text-ink/70">
                Carton weight
                <input
                  name="cartonWeight"
                  type="number"
                  step="0.01"
                  min="0.01"
                  defaultValue={product.cartonWeight ?? ""}
                  className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
                />
              </label>
              <button
                type="submit"
                className="rounded border border-secondary px-3 py-1 text-sm text-secondary hover:bg-secondary/10"
              >
                Save
              </button>
            </form>
            {product.active && (
              <form action={deactivateProductAction} className="mt-3">
                <input type="hidden" name="productId" value={product.id} />
                <button
                  type="submit"
                  className="rounded border border-error px-3 py-1 text-sm text-error hover:bg-error/10"
                >
                  Deactivate
                </button>
              </form>
            )}
          </li>
        ))}
        {products.length === 0 && <p className="text-sm text-ink/50">No products yet.</p>}
      </ul>
    </div>
  );
}
