"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

// Transparent over the hero photo, then a solid cream bar once the page has
// scrolled. Server HTML renders the transparent state, so there is no flash.
export default function LandingHeader() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-colors ${
        scrolled ? "border-b border-ink/10 bg-bg text-ink" : "bg-transparent text-on-dark"
      }`}
    >
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <span className="text-lg font-semibold">FullShelf</span>
        <nav className="flex items-center gap-5 text-sm font-medium">
          <Link href="/shop" className="underline-offset-4 hover:underline">
            Pair device
          </Link>
          <Link
            href="/sign-in"
            className={`rounded-full border px-4 py-1.5 transition-colors ${
              scrolled ? "border-primary bg-primary text-on-dark" : "border-on-dark hover:bg-on-dark hover:text-primary"
            }`}
          >
            Sign in
          </Link>
        </nav>
      </div>
    </header>
  );
}
