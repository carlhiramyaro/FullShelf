"use client";

import { useEffect, useId, useState, type Ref } from "react";
import { matchesQuery } from "@/lib/search";

export type ComboProduct = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  currentBalance: number;
};

type Props = {
  products: ComboProduct[];
  /** The text in the box. Owned by the parent so it can clear it after a save. */
  query: string;
  onQueryChange: (query: string) => void;
  selectedId: number | "";
  onSelect: (id: number | "") => void;
  /** Called when a product has just been chosen, so the parent can move focus on. */
  onPicked: () => void;
  inputRef: Ref<HTMLInputElement>;
};

// One field that is both the search box and the picker. Typing filters the
// list under the box; Enter or a tap chooses. It replaces the old
// search-box-plus-<select> pair, which needed two controls for one decision.
export default function ProductCombobox({
  products,
  query,
  onQueryChange,
  selectedId,
  onSelect,
  onPicked,
  inputRef,
}: Props) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);

  const selected = selectedId !== "";
  const matches = selected ? [] : products.filter((p) => matchesQuery(p.name, query));
  const showList = open && !selected;
  // Matches can shrink while typing; never point past the end of the list.
  const active = highlight < matches.length ? highlight : -1;

  useEffect(() => {
    if (active >= 0) {
      document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: "nearest" });
    }
  }, [active, listId]);

  function pick(product: ComboProduct) {
    onSelect(product.productId);
    onQueryChange(product.name);
    setOpen(false);
    setHighlight(-1);
    onPicked();
  }

  function clear() {
    onSelect("");
    onQueryChange("");
    setOpen(true);
    setHighlight(-1);
    if (inputRef && typeof inputRef === "object") inputRef.current?.focus();
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setHighlight(Math.min(active + 1, matches.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight(Math.max(active - 1, 0));
    } else if (e.key === "Enter") {
      // Enter here must never submit the form: it picks, or moves on.
      e.preventDefault();
      if (selected) onPicked();
      else if (showList && matches[active]) pick(matches[active]);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  return (
    <div className="relative">
      <label className="flex flex-col text-sm text-ink/70">
        Product
        <div className="relative">
          <input
            ref={inputRef}
            type="text"
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
            autoComplete="off"
            value={query}
            placeholder="Type to search products…"
            onChange={(e) => {
              onQueryChange(e.target.value);
              onSelect("");
              setOpen(true);
              setHighlight(e.target.value.trim() ? 0 : -1);
            }}
            onFocus={() => setOpen(true)}
            onBlur={() => setOpen(false)}
            onKeyDown={onKeyDown}
            className="w-full rounded border border-ink/20 bg-bg py-1 pl-2 pr-9"
          />
          {query && (
            <button
              type="button"
              onClick={clear}
              aria-label="Clear product"
              className="absolute inset-y-0 right-0 px-3 text-ink/60"
            >
              ×
            </button>
          )}
        </div>
      </label>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded border border-ink/20 bg-bg shadow"
        >
          {matches.length === 0 ? (
            <li className="px-3 py-2 text-sm text-ink/60">No products match</li>
          ) : (
            matches.map((product, i) => (
              <li
                key={product.productId}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                // mousedown would blur the input and close the list before the click lands.
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(product)}
                className={`cursor-pointer px-3 py-2.5 text-sm text-ink ${
                  i === active ? "bg-secondary text-on-dark" : ""
                }`}
              >
                {product.name}
                <span className={i === active ? "text-on-dark/80" : "text-ink/60"}>
                  {" "}
                  — currently {product.currentBalance} {product.unit === "UNIT" ? "units" : "kg"}
                </span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
