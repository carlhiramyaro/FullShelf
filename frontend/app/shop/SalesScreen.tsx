"use client";

import { useEffect, useRef, useState } from "react";
import { API_BASE_URL } from "@/lib/api";
import ReceiptView, { ReceiptData } from "./ReceiptView";
import TodaysSalesScreen from "./TodaysSalesScreen";

const STAFF_SESSION_KEY = "fullshelf_staff_session";

type SaleableProduct = { productId: number; name: string; unit: "KG" | "UNIT"; price: number };

type CartLine = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  price: number;
  quantity: number;
  discount: string;
};

async function errorMessage(res: Response, fallback: string): Promise<string> {
  const body = await res.json().catch(() => null);
  return (body as { message?: string } | null)?.message ?? fallback;
}

// Every word typed must appear somewhere in the name, so "kivo" finds all
// Kivo products and "kivo tom" narrows to Kivo Tomato Paste.
function matchesQuery(name: string, query: string): boolean {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  const lower = name.toLowerCase();
  return words.every((word) => lower.includes(word));
}

function sessionHeaders(): HeadersInit {
  const token = sessionStorage.getItem(STAFF_SESSION_KEY);
  return token ? { "X-Staff-Session-Token": token } : {};
}

// The staff-facing confirm screen from mvp.md's "Staff screen": tap a
// product tile, adjust its quantity/discount in the cart below, Confirm
// saves the sale. No server-side balance check backs this — mvp.md allows
// selling past zero, and flagging it is a later (Phase F) dashboard concern.
export default function SalesScreen({
  staff,
  onSwitchUser,
}: {
  staff: { id: number; name: string };
  onSwitchUser: () => void;
}) {
  const [products, setProducts] = useState<SaleableProduct[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [receipt, setReceipt] = useState<ReceiptData | null>(null);
  const [showTodaysSales, setShowTodaysSales] = useState(false);
  const [query, setQuery] = useState("");
  const [orderOpen, setOrderOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/staff/sales`, { headers: sessionHeaders() }).then(async (res) => {
      if (res.status === 401) {
        onSwitchUser();
        return;
      }
      if (res.ok) setProducts(await res.json());
    });
  }, [onSwitchUser]);

  function addToCart(product: SaleableProduct) {
    setError(null);
    setCart((prev) => {
      if (prev.some((line) => line.productId === product.productId)) return prev;
      // Newest on top so the item just tapped is visible in a long order.
      return [
        { productId: product.productId, name: product.name, unit: product.unit, price: product.price, quantity: 1, discount: "" },
        ...prev,
      ];
    });
    setQuery("");
    searchRef.current?.focus();
  }

  function updateQuantity(productId: number, quantity: number) {
    setCart((prev) => prev.map((line) => (line.productId === productId ? { ...line, quantity } : line)));
  }

  function updateDiscount(productId: number, discount: string) {
    setCart((prev) => prev.map((line) => (line.productId === productId ? { ...line, discount } : line)));
  }

  function removeLine(productId: number) {
    setCart((prev) => prev.filter((line) => line.productId !== productId));
  }

  function lineTotal(line: CartLine): number {
    const discount = Number(line.discount) || 0;
    return Math.max(0, line.quantity * line.price - discount);
  }

  const total = cart.reduce((sum, line) => sum + lineTotal(line), 0);

  // Confirm is disabled while a request is in flight so a fast double-tap
  // can't fire two sales — the only double-submit guard this slice needs;
  // a persisted idempotency key was judged unnecessary for the pilot.
  async function handleConfirm() {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/staff/sales`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...sessionHeaders() },
        body: JSON.stringify({
          lines: cart.map((line) => ({
            productId: line.productId,
            quantity: line.quantity,
            discount: line.discount ? Number(line.discount) : 0,
          })),
        }),
      });
      if (res.status === 401) {
        onSwitchUser();
        return;
      }
      if (!res.ok) {
        throw new Error(await errorMessage(res, "Could not save the sale"));
      }
      setReceipt((await res.json()) as ReceiptData);
      setCart([]);
      setOrderOpen(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save the sale");
    } finally {
      setBusy(false);
    }
  }

  if (showTodaysSales) {
    return <TodaysSalesScreen onBack={() => setShowTodaysSales(false)} onSwitchUser={onSwitchUser} />;
  }

  if (receipt) {
    return (
      <ReceiptView
        receipt={receipt}
        footer={
          <>
            <button
              onClick={() => setReceipt(null)}
              className="rounded bg-primary px-4 py-2 text-on-dark"
            >
              New sale
            </button>
            <button onClick={onSwitchUser} className="text-sm text-secondary underline">
              Switch user
            </button>
          </>
        }
      />
    );
  }

  const visible = products.filter((p) => matchesQuery(p.name, query));
  const itemCount = cart.length;

  return (
    <div className="flex h-dvh flex-col bg-bg">
      <div className="flex items-center justify-between p-4 pb-2">
        <p className="text-sm text-ink/50">
          Serving: <span className="font-medium text-ink">{staff.name}</span>
        </p>
        <div className="flex items-center gap-4">
          <button onClick={() => setShowTodaysSales(true)} className="text-sm text-secondary underline">
            Today&apos;s sales
          </button>
          <button onClick={onSwitchUser} className="text-sm text-secondary underline">
            Switch user
          </button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col md:grid md:grid-cols-[1fr_minmax(320px,380px)]">
        <div className="flex min-h-0 flex-1 flex-col gap-3 p-4 pt-2">
          <div className="relative">
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search products…"
              aria-label="Search products"
              className="w-full rounded-lg border border-ink/20 bg-bg px-3 py-2 pr-10 text-ink"
            />
            {query && (
              <button
                onClick={() => {
                  setQuery("");
                  searchRef.current?.focus();
                }}
                aria-label="Clear search"
                className="absolute right-2 top-1/2 -translate-y-1/2 px-2 text-lg text-secondary"
              >
                ×
              </button>
            )}
          </div>

          <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-2 gap-3 overflow-y-auto sm:grid-cols-3">
            {visible.map((product) => {
              const inOrder = cart.some((line) => line.productId === product.productId);
              return (
                <button
                  key={product.productId}
                  onClick={() => addToCart(product)}
                  className={`rounded-lg border p-4 text-left shadow-sm hover:bg-secondary/10 ${
                    inOrder ? "border-primary bg-primary/5" : "border-ink/10"
                  }`}
                >
                  <p className="font-medium text-ink">{product.name}</p>
                  <p className="text-xs text-ink/50">
                    GH₵{product.price.toFixed(2)} / {product.unit === "UNIT" ? "unit" : "kg"}
                  </p>
                  {inOrder && <p className="mt-1 text-xs font-medium text-primary">✓ In order</p>}
                </button>
              );
            })}
            {products.length === 0 && (
              <p className="col-span-full text-center text-sm text-ink/50">No products set up yet.</p>
            )}
            {products.length > 0 && visible.length === 0 && (
              <p className="col-span-full text-center text-sm text-ink/50">
                No products match &quot;{query}&quot;
              </p>
            )}
          </div>
        </div>

        {/* One OrderPanel instance: a side column from md up, a full-screen
            overlay on phones when opened from the bottom bar. */}
        <div
          className={
            orderOpen
              ? "fixed inset-0 z-10 flex flex-col bg-bg md:static md:z-auto"
              : "hidden min-h-0 flex-col border-l border-ink/10 md:flex"
          }
        >
          <OrderPanel
            cart={cart}
            total={total}
            busy={busy}
            error={error}
            onConfirm={handleConfirm}
            onClose={() => setOrderOpen(false)}
            onQuantityChange={updateQuantity}
            onDiscountChange={updateDiscount}
            onRemove={removeLine}
          />
        </div>
      </div>

      {itemCount > 0 && (
        <div className="flex items-center justify-between gap-3 border-t border-ink/10 bg-bg p-3 md:hidden">
          <p className="font-medium text-ink">
            {itemCount} {itemCount === 1 ? "item" : "items"} · GH₵{total.toFixed(2)}
          </p>
          <button
            onClick={() => setOrderOpen(true)}
            className="rounded bg-secondary px-4 py-2 font-medium text-on-dark"
          >
            View order ▸
          </button>
        </div>
      )}
    </div>
  );
}

function OrderPanel({
  cart,
  total,
  busy,
  error,
  onConfirm,
  onClose,
  onQuantityChange,
  onDiscountChange,
  onRemove,
}: {
  cart: CartLine[];
  total: number;
  busy: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
  onQuantityChange: (productId: number, quantity: number) => void;
  onDiscountChange: (productId: number, discount: string) => void;
  onRemove: (productId: number) => void;
}) {
  return (
    <>
      <div className="flex items-center justify-between p-4 pb-2">
        <h2 className="font-semibold text-ink">Order</h2>
        <button onClick={onClose} className="text-sm text-secondary underline md:hidden">
          ← Add more
        </button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-4">
        {cart.length === 0 && <p className="text-sm text-ink/50">Tap a product to add it</p>}
        {cart.map((line) => (
          <CartLineRow
            key={line.productId}
            line={line}
            onQuantityChange={(q) => onQuantityChange(line.productId, q)}
            onDiscountChange={(d) => onDiscountChange(line.productId, d)}
            onRemove={() => onRemove(line.productId)}
          />
        ))}
      </div>

      <div className="flex flex-col gap-3 border-t border-ink/10 p-4">
        {error && <p className="text-sm text-ink">⚠ {error}</p>}
        <p className="text-2xl font-semibold text-primary">Total: GH₵{total.toFixed(2)}</p>
        <button
          onClick={onConfirm}
          disabled={busy || cart.length === 0}
          className="rounded bg-primary px-6 py-3 font-medium text-on-dark disabled:opacity-50"
        >
          {busy ? "Saving…" : "Confirm"}
        </button>
      </div>
    </>
  );
}

function CartLineRow({
  line,
  onQuantityChange,
  onDiscountChange,
  onRemove,
}: {
  line: CartLine;
  onQuantityChange: (quantity: number) => void;
  onDiscountChange: (discount: string) => void;
  onRemove: () => void;
}) {
  const [freeEntry, setFreeEntry] = useState(String(line.quantity));

  return (
    <div className="flex flex-col gap-2 border-b border-ink/10 pb-3 last:border-0 last:pb-0">
      <div className="flex items-center justify-between">
        <p className="font-medium text-ink">{line.name}</p>
        <button onClick={onRemove} className="text-xs text-secondary underline">
          Remove
        </button>
      </div>

      {line.unit === "KG" ? (
        <div className="flex flex-wrap items-center gap-2">
          {[0.5, 1, 2].map((amount) => (
            <button
              key={amount}
              onClick={() => {
                onQuantityChange(amount);
                setFreeEntry(String(amount));
              }}
              className={`rounded border px-3 py-1 text-sm ${
                line.quantity === amount ? "border-primary bg-primary/10" : "border-ink/20"
              }`}
            >
              {amount} kg
            </button>
          ))}
          <input
            type="number"
            step="0.01"
            min="0.01"
            value={freeEntry}
            onChange={(e) => {
              setFreeEntry(e.target.value);
              const value = Number(e.target.value);
              if (e.target.value && !Number.isNaN(value) && value > 0) onQuantityChange(value);
            }}
            className="w-20 rounded border border-ink/20 bg-bg px-2 py-1 text-sm"
          />
        </div>
      ) : (
        <div className="flex items-center gap-3">
          <button
            onClick={() => onQuantityChange(Math.max(1, line.quantity - 1))}
            className="h-8 w-8 rounded border border-ink/20 text-lg"
          >
            −
          </button>
          <span className="w-8 text-center">{line.quantity}</span>
          <button
            onClick={() => onQuantityChange(line.quantity + 1)}
            className="h-8 w-8 rounded border border-ink/20 text-lg"
          >
            +
          </button>
        </div>
      )}

      <label className="flex items-center gap-2 text-sm text-ink/70">
        Discount (GH₵)
        <input
          type="number"
          step="0.01"
          min="0"
          value={line.discount}
          onChange={(e) => onDiscountChange(e.target.value)}
          placeholder="0.00"
          className="w-24 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      <p className="text-sm text-ink/50">
        {line.quantity} × GH₵{line.price.toFixed(2)} = GH₵{(line.quantity * line.price).toFixed(2)}
        {Number(line.discount) > 0 && <> − GH₵{Number(line.discount).toFixed(2)} discount</>}
      </p>
    </div>
  );
}
