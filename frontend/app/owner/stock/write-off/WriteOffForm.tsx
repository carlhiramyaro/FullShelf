"use client";

import { useActionState, useState } from "react";
import { writeOffAction, WriteOffState } from "./actions";

type Product = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  currentBalance: number;
};

const initialState: WriteOffState = {};

export default function WriteOffForm({ products }: { products: Product[] }) {
  const [state, formAction, pending] = useActionState(writeOffAction, initialState);
  const [productId, setProductId] = useState<number | "">("");

  const selectedProduct = products.find((p) => p.productId === productId) ?? null;
  const unitLabel = selectedProduct?.unit === "UNIT" ? "units" : "kg";

  return (
    <form action={formAction} className="flex flex-col gap-3 rounded-lg border border-ink/10 p-4">
      <label className="flex flex-col text-sm text-ink/70">
        Product
        <select
          name="productId"
          required
          value={productId}
          onChange={(e) => setProductId(e.target.value ? Number(e.target.value) : "")}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        >
          <option value="">Select a product</option>
          {products.map((product) => (
            <option key={product.productId} value={product.productId}>
              {product.name} — currently {product.currentBalance} {product.unit === "UNIT" ? "units" : "kg"}
            </option>
          ))}
        </select>
      </label>

      <label className="flex flex-col text-sm text-ink/70">
        Quantity lost ({unitLabel})
        <input
          name="quantity"
          type="number"
          step="0.01"
          min="0.01"
          required
          className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm text-ink/70">
        Note (required — spoilage, damage, etc.)
        <textarea
          name="note"
          required
          rows={2}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      <button
        type="submit"
        disabled={pending}
        className="mt-1 self-start rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Record write-off"}
      </button>

      {state.error && <p className="text-sm text-error">{state.error}</p>}
      {state.result && (
        <p className="text-sm text-primary">
          Saved: {state.result.quantity} {unitLabel} written off ({state.result.note}).
        </p>
      )}
    </form>
  );
}
