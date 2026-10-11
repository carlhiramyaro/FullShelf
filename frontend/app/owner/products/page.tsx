import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { deactivateProductAction } from "./actions";
import ProductForm, { Product } from "./ProductForm";

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

      <div className="mb-8 rounded-lg border border-ink/10 p-4">
        <ProductForm />
      </div>

      <ul className="flex flex-col gap-3">
        {products.map((product) => (
          <li key={product.id} className="rounded-lg border border-ink/10 p-4">
            <ProductForm product={product} />
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
