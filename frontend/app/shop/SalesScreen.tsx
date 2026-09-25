"use client";

import { useEffect, useState } from "react";
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
      return [
        ...prev,
        { productId: product.productId, name: product.name, unit: product.unit, price: product.price, quantity: 1, discount: "" },
      ];
    });
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

  return (
    <div className="flex min-h-screen flex-col gap-4 bg-bg p-4">
      <div className="flex items-center justify-between">
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

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {products.map((product) => (
          <button
            key={product.productId}
            onClick={() => addToCart(product)}
            className="rounded-lg border border-ink/10 p-4 text-left shadow-sm hover:bg-secondary/10"
          >
            <p className="font-medium text-ink">{product.name}</p>
            <p className="text-xs text-ink/50">
              GH₵{product.price.toFixed(2)} / {product.unit === "UNIT" ? "unit" : "kg"}
            </p>
          </button>
        ))}
        {products.length === 0 && (
          <p className="col-span-full text-center text-sm text-ink/50">No products set up yet.</p>
        )}
      </div>

      {cart.length > 0 && (
        <div className="flex flex-col gap-3 rounded-lg border border-ink/10 p-4">
          {cart.map((line) => (
            <CartLineRow
              key={line.productId}
              line={line}
              onQuantityChange={(q) => updateQuantity(line.productId, q)}
              onDiscountChange={(d) => updateDiscount(line.productId, d)}
              onRemove={() => removeLine(line.productId)}
            />
          ))}
          <div className="flex items-center justify-between border-t border-ink/10 pt-3">
            <p className="text-lg font-semibold text-ink">Total: GH₵{total.toFixed(2)}</p>
            <button
              onClick={handleConfirm}
              disabled={busy}
              className="rounded bg-primary px-6 py-2 font-medium text-on-dark disabled:opacity-50"
            >
              {busy ? "Saving…" : "Confirm"}
            </button>
          </div>
        </div>
      )}

      {error && <p className="text-sm text-error">{error}</p>}
    </div>
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
        <button onClick={onRemove} className="text-xs text-error underline">
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
