import { auth } from "@clerk/nextjs/server";
import Link from "next/link";
import { redirect } from "next/navigation";
import { API_BASE_URL } from "@/lib/api";
import SignOut from "../_components/SignOut";

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
    <div className="min-h-screen bg-bg p-8">
      <nav className="mb-8 flex flex-wrap gap-6 text-sm font-medium text-secondary">
        <Link href="/owner" className="hover:text-primary">
          Dashboard
        </Link>
        <Link href="/owner/staff" className="hover:text-primary">
          Staff
        </Link>
        <Link href="/owner/devices" className="hover:text-primary">
          Devices
        </Link>
        <Link href="/owner/products" className="hover:text-primary">
          Products
        </Link>
        <Link href="/owner/stock" className="hover:text-primary">
          Opening stock
        </Link>
        <Link href="/owner/stock/levels" className="hover:text-primary">
          Stock levels
        </Link>
        <Link href="/owner/stock/receive" className="hover:text-primary">
          Receive stock
        </Link>
        <Link href="/owner/stock/write-off" className="hover:text-primary">
          Write off
        </Link>
        <Link href="/owner/stock/reverse" className="hover:text-primary">
          Reverse entry
        </Link>
        <Link href="/owner/sales" className="hover:text-primary">
          Sales
        </Link>
        <Link href="/owner/day-close" className="hover:text-primary">
          Day close
        </Link>
        <Link href="/owner/settings" className="hover:text-primary">
          Settings
        </Link>
        <span className="ml-auto">
          <SignOut />
        </span>
      </nav>
      {children}
    </div>
  );
}
