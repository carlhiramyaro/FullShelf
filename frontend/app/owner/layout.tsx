import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { API_BASE_URL } from "@/lib/api";
import OwnerNav from "./_components/OwnerNav";

// A signed-in user the backend does not know as an owner (no shop, or a replaced
// login) is sent to /no-shop instead of hitting a 403 on every page.
export default async function OwnerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { getToken } = await auth();
  const res = await fetch(`${API_BASE_URL}/api/owner/me`, {
    headers: { Authorization: `Bearer ${await getToken()}` },
    cache: "no-store",
  });
  if (res.status === 401 || res.status === 403) {
    redirect("/no-shop");
  }

  return (
    <div className="min-h-screen bg-bg">
      <OwnerNav />
      <main className="p-4 md:ml-56 md:p-8">{children}</main>
    </div>
  );
}
