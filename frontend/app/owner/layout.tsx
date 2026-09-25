import Link from "next/link";

export default function OwnerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-bg p-8">
      <nav className="mb-8 flex gap-6 text-sm font-medium text-secondary">
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
      </nav>
      {children}
    </div>
  );
}
