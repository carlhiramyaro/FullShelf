import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import WriteOffForm from "./WriteOffForm";

type ProductWriteOffStatus = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  currentBalance: number;
};

async function fetchProducts(): Promise<ProductWriteOffStatus[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/write-off`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load products: ${res.status}`);
  }
  return res.json();
}

export default async function WriteOffPage() {
  const products = await fetchProducts();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Write off stock</h1>
      <p className="mb-6 text-sm text-ink/60">
        Record stock lost outside a sale, such as spoilage or damage. A note is required so
        there&apos;s always a reason on the record.
      </p>

      {products.length > 0 ? (
        <WriteOffForm products={products} />
      ) : (
        <p className="text-sm text-ink/50">No active products yet.</p>
      )}
    </div>
  );
}
