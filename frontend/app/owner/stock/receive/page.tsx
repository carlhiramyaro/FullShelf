import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import ReceiveStockForm from "./ReceiveStockForm";

type ProductReceiveStatus = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  cartonWeight: number | null;
  currentBalance: number;
};

async function fetchProducts(): Promise<ProductReceiveStatus[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/receive`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load products: ${res.status}`);
  }
  return res.json();
}

export default async function ReceiveStockPage() {
  const products = await fetchProducts();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Receive stock</h1>
      <p className="mb-6 text-sm text-ink/60">
        Enter one delivery at a time. Carton count is optional — fill it in when a delivery
        came in cartons and you want to see the gap against the nominal weight.
      </p>

      {products.length > 0 ? (
        <ReceiveStockForm products={products} />
      ) : (
        <p className="text-sm text-ink/50">No active products yet.</p>
      )}
    </div>
  );
}
