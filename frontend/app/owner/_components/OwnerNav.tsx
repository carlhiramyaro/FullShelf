"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import SignOut from "../../_components/SignOut";

// Same links and order the old top row had. `exact` links only match their own
// path, because /owner and /owner/stock are prefixes of other entries.
const LINKS = [
  { href: "/owner", label: "Dashboard", exact: true },
  { href: "/owner/staff", label: "Staff" },
  { href: "/owner/devices", label: "Devices" },
  { href: "/owner/products", label: "Products" },
  { href: "/owner/stock", label: "Opening stock", exact: true },
  { href: "/owner/stock/levels", label: "Stock levels" },
  { href: "/owner/stock/receive", label: "Receive stock" },
  { href: "/owner/stock/write-off", label: "Write off" },
  { href: "/owner/stock/reverse", label: "Reverse entry" },
  { href: "/owner/sales", label: "Sales" },
  { href: "/owner/day-close", label: "Day close" },
  { href: "/owner/settings", label: "Settings" },
];

const rowClass = "block min-h-11 border-l-4 px-4 py-3 text-sm font-medium";

// One list, rendered once. From md up it is a fixed sidebar; below that the same
// element slides in over the page when the hamburger is tapped.
export default function OwnerNav() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);

  useEffect(() => {
    if (open) {
      closeButton.current?.focus();
      document.body.style.overflow = "hidden";
      const onKey = (e: KeyboardEvent) => {
        if (e.key === "Escape") setOpen(false);
      };
      window.addEventListener("keydown", onKey);
      wasOpen.current = true;
      return () => {
        document.body.style.overflow = "";
        window.removeEventListener("keydown", onKey);
      };
    }
    if (wasOpen.current) {
      menuButton.current?.focus();
      wasOpen.current = false;
    }
  }, [open]);

  return (
    <>
      <header className="sticky top-0 z-30 flex h-14 items-center gap-3 bg-primary px-4 text-on-dark md:hidden">
        <button
          ref={menuButton}
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Open menu"
          aria-expanded={open}
          aria-controls="owner-nav"
          className="-ml-2 flex size-11 items-center justify-center"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <span className="text-lg font-semibold">FullShelf</span>
      </header>

      {open && (
        <div
          className="fixed inset-0 z-40 bg-ink/40 md:hidden"
          onClick={() => setOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        id="owner-nav"
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-secondary/20 bg-bg transition-transform md:z-20 md:w-56 md:translate-x-0 ${
          open ? "translate-x-0" : "-translate-x-full max-md:invisible"
        }`}
      >
        <div className="flex h-14 shrink-0 items-center justify-between px-4">
          <span className="text-lg font-semibold text-primary">FullShelf</span>
          <button
            ref={closeButton}
            type="button"
            onClick={() => setOpen(false)}
            aria-label="Close menu"
            className="-mr-2 flex size-11 items-center justify-center text-secondary md:hidden"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <nav aria-label="Owner" className="min-h-0 flex-1 overflow-y-auto py-2">
          {LINKS.map(({ href, label, exact }) => {
            const active = exact ? pathname === href : pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                onClick={() => setOpen(false)}
                aria-current={active ? "page" : undefined}
                className={`${rowClass} ${
                  active
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-transparent text-secondary hover:text-primary"
                }`}
              >
                {label}
              </Link>
            );
          })}
        </nav>

        <div className="shrink-0 border-t border-secondary/20 py-2">
          <SignOut className={`${rowClass} w-full border-transparent text-left text-secondary hover:text-primary`} />
        </div>
      </aside>
    </>
  );
}
