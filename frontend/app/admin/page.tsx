import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import CreateShopForm from "./CreateShopForm";
import RelinkOwnerForm from "./RelinkOwnerForm";

type ShopSummary = {
  id: number;
  name: string;
  createdAt: string;
  activeStaff: number;
  owner: { name: string; email: string; phoneNumber: string | null; status: "active" | "pending" } | null;
};

async function fetchShops(): Promise<ShopSummary[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/admin/shops`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load shops: ${res.status}`);
  }
  return res.json();
}

export default async function AdminPage() {
  const shops = await fetchShops();

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">Shops</h1>

      <div className="mb-8 rounded-lg border border-ink/10 p-4">
        <h2 className="mb-3 text-sm font-medium text-ink">Create a shop</h2>
        <CreateShopForm />
      </div>

      <ul className="flex flex-col gap-3">
        {shops.map((shop) => (
          <li key={shop.id} className="rounded-lg border border-ink/10 p-4">
            <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
              <span className="font-medium text-ink">{shop.name}</span>
              <span className="text-xs text-ink/60">
                Created {shop.createdAt.slice(0, 10)} · {shop.activeStaff} active staff
              </span>
            </div>
            <p className="mb-3 text-sm text-ink/80">
              {shop.owner ? (
                <>
                  {shop.owner.name} ({shop.owner.email}){" "}
                  {shop.owner.status === "active" ? (
                    <span className="text-primary">✓ Signed in</span>
                  ) : (
                    <span className="text-alert-text">⏳ Waiting for first sign-in</span>
                  )}
                </>
              ) : (
                <span className="text-alert-text">⚠ No owner</span>
              )}
            </p>
            <RelinkOwnerForm shopId={shop.id} hasActiveOwner={shop.owner?.status === "active"} />
          </li>
        ))}
      </ul>
    </div>
  );
}
