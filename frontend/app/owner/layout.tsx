import Link from "next/link";

export default function OwnerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-zinc-50 p-8 dark:bg-black">
      <nav className="mb-8 flex gap-6 text-sm font-medium text-zinc-600 dark:text-zinc-400">
        <Link href="/owner/staff" className="hover:text-zinc-900 dark:hover:text-zinc-50">
          Staff
        </Link>
        <Link href="/owner/devices" className="hover:text-zinc-900 dark:hover:text-zinc-50">
          Devices
        </Link>
      </nav>
      {children}
    </div>
  );
}
