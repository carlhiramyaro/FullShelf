"use client";

import { useActionState, useMemo, useState } from "react";
import { receiveStockAction, ReceiveStockState } from "./actions";

type Product = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  cartonWeight: number | null;
  currentBalance: number;
};

const initialState: ReceiveStockState = {};

export default function ReceiveStockForm({ products }: { products: Product[] }) {
  const [state, formAction, pending] = useActionState(receiveStockAction, initialState);
  const [productId, setProductId] = useState<number | "">("");
  const [cartonCount, setCartonCount] = useState("");
  const [actualQuantity, setActualQuantity] = useState("");

  const selectedProduct = products.find((p) => p.productId === productId) ?? null;
  const unitLabel = selectedProduct?.unit === "UNIT" ? "units" : "kg";

  const preview = useMemo(() => {
    if (!selectedProduct || selectedProduct.cartonWeight == null) return null;
    const cartons = Number(cartonCount);
    const actual = Number(actualQuantity);
    if (!cartonCount || !actualQuantity || Number.isNaN(cartons) || Number.isNaN(actual)) return null;
    const nominal = cartons * selectedProduct.cartonWeight;
    const gap = actual - nominal;
    return { nominal, gap };
  }, [selectedProduct, cartonCount, actualQuantity]);

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
        Carton count (optional)
        <input
          name="cartonCount"
          type="number"
          step="1"
          min="1"
          value={cartonCount}
          onChange={(e) => setCartonCount(e.target.value)}
          className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm text-ink/70">
        Actual {unitLabel} received
        <input
          name="actualQuantity"
          type="number"
          step="0.01"
          min="0.01"
          required
          value={actualQuantity}
          onChange={(e) => setActualQuantity(e.target.value)}
          className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      {preview && (
        <p className="text-sm text-ink/60">
          Nominal {preview.nominal.toFixed(2)} {unitLabel} — actual is{" "}
          <span className={preview.gap < 0 ? "text-error" : preview.gap > 0 ? "text-primary" : ""}>
            {preview.gap > 0 ? "+" : ""}
            {preview.gap.toFixed(2)} {unitLabel}
          </span>{" "}
          {preview.gap < 0 ? "short" : preview.gap > 0 ? "over" : "exact"}
        </p>
      )}

      <button
        type="submit"
        disabled={pending}
        className="mt-1 self-start rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Record delivery"}
      </button>

      {state.error && <p className="text-sm text-error">{state.error}</p>}
      {state.result && (
        <p className="text-sm text-primary">
          Saved: {state.result.actualQuantity} {unitLabel} received
          {state.result.gap != null && (
            <>
              {" "}
              ({state.result.gap > 0 ? "+" : ""}
              {state.result.gap} {unitLabel} vs nominal {state.result.nominal})
            </>
          )}
          .
        </p>
      )}
    </form>
  );
}
