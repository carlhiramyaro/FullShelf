"use client";

import { createContext, useContext, useState, type ReactNode } from "react";
import { matchesQuery } from "@/lib/search";

const QueryContext = createContext("");

// Search box for the owner's server-rendered product lists. The page stays a
// Server Component and passes its rows in as children; each row is wrapped in
// <SearchMatch>, which hides itself when the name doesn't match. Rows are
// hidden, not removed, so a quantity typed into an Opening stock row or a
// half-edited product form survives a search that hides it (and hidden inputs
// still submit).
export function ProductSearch({ children }: { children: ReactNode }) {
  const [query, setQuery] = useState("");

  return (
    <QueryContext.Provider value={query}>
      <div className="relative mb-4">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search products…"
          aria-label="Search products"
          className="w-full rounded-lg border border-ink/20 bg-bg px-3 py-2 pr-10 text-ink"
        />
        {query && (
          <button
            type="button"
            onClick={() => setQuery("")}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 px-2 text-lg text-secondary"
          >
            ×
          </button>
        )}
      </div>
      {children}
    </QueryContext.Provider>
  );
}

export function SearchMatch({
  name,
  as: Tag = "div",
  className,
  children,
}: {
  name: string;
  as?: "div" | "li";
  className?: string;
  children: ReactNode;
}) {
  const query = useContext(QueryContext);
  return (
    <Tag className={className} hidden={!matchesQuery(name, query)}>
      {children}
    </Tag>
  );
}
