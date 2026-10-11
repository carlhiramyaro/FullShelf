import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import SignOut from "../_components/SignOut";

// The backend decides who is admin (ADMIN_CLERK_USER_ID); this only asks, so
// anyone else sees a plain message instead of a broken page.
export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/admin/me`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });

  return (
    <div className="min-h-screen bg-bg p-8">
      <div className="mb-8 flex justify-end">
        <SignOut />
      </div>
      {res.ok ? (
        children
      ) : (
        <p className="mx-auto max-w-2xl text-ink">This account does not have admin access.</p>
      )}
    </div>
  );
}
