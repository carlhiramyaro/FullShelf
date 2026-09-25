import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import ReverseEntryForm from "./ReverseEntryForm";

type ReversibleMovement = {
  movementId: number;
  type: "OPENING" | "RECEIVED";
  quantity: number;
  note: string | null;
  createdAt: string;
};

async function fetchMovements(productId: string): Promise<ReversibleMovement[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/reverse/${productId}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load entries: ${res.status}`);
  }
  return res.json();
}

export default async function ReverseEntryProductPage({
  params,
}: {
  params: Promise<{ productId: string }>;
}) {
  const { productId } = await params;
  const movements = await fetchMovements(productId);

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/owner/stock/reverse" className="mb-4 inline-block text-sm text-primary hover:underline">
        ← Back
      </Link>
      <h1 className="mb-6 text-2xl font-semibold text-ink">Reverse an entry</h1>

      {movements.length > 0 ? (
        <ReverseEntryForm productId={Number(productId)} movements={movements} />
      ) : (
        <p className="text-sm text-ink/50">
          Nothing left to reverse for this product — every opening/received line is either
          current or already reversed.
        </p>
      )}
    </div>
  );
}
