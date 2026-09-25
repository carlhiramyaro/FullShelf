import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";

type ProductReverseStatus = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  reversibleCount: number;
  currentBalance: number;
};

async function fetchProducts(): Promise<ProductReverseStatus[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/reverse`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load products: ${res.status}`);
  }
  return res.json();
}

export default async function ReverseEntryPage() {
  const products = await fetchProducts();
  const reversible = products.filter((product) => product.reversibleCount > 0);
  const clean = products.filter((product) => product.reversibleCount === 0);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Reverse entry</h1>
      <p className="mb-6 text-sm text-ink/60">
        Undo a mistyped opening or received line. The original stays visible in the log — this
        adds a reversing line, then you re-enter the correct figure from the Opening stock or
        Receive stock screen.
      </p>

      {reversible.length > 0 ? (
        <div className="mb-8 rounded-lg border border-ink/10 p-4">
          <h2 className="mb-3 text-sm font-medium text-ink/70">Has entries to reverse</h2>
          <ul className="flex flex-col gap-1 text-sm">
            {reversible.map((product) => (
              <li key={product.productId} className="flex justify-between">
                <Link
                  href={`/owner/stock/reverse/${product.productId}`}
                  className="text-primary hover:underline"
                >
                  {product.name}
                </Link>
                <span className="text-ink/50">
                  {product.reversibleCount} entr{product.reversibleCount === 1 ? "y" : "ies"}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="mb-8 text-sm text-ink/50">No opening or received lines to reverse right now.</p>
      )}

      {clean.length > 0 && (
        <div className="rounded-lg border border-ink/10 p-4">
          <h2 className="mb-3 text-sm font-medium text-ink/70">Nothing to reverse</h2>
          <ul className="flex flex-col gap-1 text-sm text-ink/40">
            {clean.map((product) => (
              <li key={product.productId}>{product.name}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
