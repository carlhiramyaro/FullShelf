"use client";

import { useActionState } from "react";
import { createProductAction, updateProductAction, ProductFormState } from "./actions";

export type Product = {
  id: number;
  name: string;
  unit: "KG" | "UNIT";
  price: number;
  alertLevel: number;
  cartonWeight: number | null;
  active: boolean;
};

const initialState: ProductFormState = {};

// One form for both "add" (no product) and "edit" (an existing product), so
// the two can't drift apart. After a rejected save the typed values come back
// in the state and are used as defaults, since React resets the form after
// every submit.
export default function ProductForm({ product }: { product?: Product }) {
  const [state, formAction, pending] = useActionState(
    product ? updateProductAction : createProductAction,
    initialState,
  );
  const v = state.values;

  return (
    <form action={formAction} className="flex flex-wrap items-end gap-3">
      {product && <input type="hidden" name="productId" value={product.id} />}
      <label className="flex flex-col text-sm text-ink/70">
        Name
        <input
          name="name"
          required
          defaultValue={v?.name ?? product?.name ?? ""}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        />
        {product && !product.active && <span className="mt-1 text-xs text-ink/50">deactivated</span>}
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Unit
        <select
          name="unit"
          required
          defaultValue={v?.unit ?? product?.unit ?? "KG"}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        >
          <option value="KG">kg</option>
          <option value="UNIT">unit</option>
        </select>
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Price (GH₵)
        <input
          name="price"
          type="number"
          step="0.01"
          min="0.01"
          required
          defaultValue={v?.price ?? product?.price ?? ""}
          className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Alert level
        <input
          name="alertLevel"
          type="number"
          step="0.01"
          min="0"
          required
          defaultValue={v?.alertLevel ?? product?.alertLevel ?? ""}
          className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Carton weight
        <input
          name="cartonWeight"
          type="number"
          step="0.01"
          min="0.01"
          defaultValue={v?.cartonWeight ?? product?.cartonWeight ?? ""}
          className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={pending}
        className={
          product
            ? "rounded border border-secondary px-3 py-1 text-sm text-secondary hover:bg-secondary/10 disabled:opacity-50"
            : "rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
        }
      >
        {pending ? "Saving…" : product ? "Save" : "Add product"}
      </button>
      {state.error && (
        <p role="alert" className="basis-full text-sm text-error">
          ⚠ {state.error}
        </p>
      )}
    </form>
  );
}
