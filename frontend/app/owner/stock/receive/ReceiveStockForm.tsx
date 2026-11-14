"use client";

import Link from "next/link";
import { useActionState, useMemo, useRef, useState } from "react";
import ProductCombobox from "./ProductCombobox";
import { receiveStockAction, ReceiveStockState } from "./actions";

type Product = {
  productId: number;
  name: string;
  unit: "KG" | "UNIT";
  cartonWeight: number | null;
  currentBalance: number;
  costPrice: number | null;
};

type Recorded = {
  id: number;
  name: string;
  unit: "KG" | "UNIT";
  actualQuantity: number;
  gap: number | null;
  nominal: number | null;
};

const initialState: ReceiveStockState = {};

export default function ReceiveStockForm({ products }: { products: Product[] }) {
  const [productId, setProductId] = useState<number | "">("");
  const [query, setQuery] = useState("");
  const [cartonCount, setCartonCount] = useState("");
  const [actualQuantity, setActualQuantity] = useState("");
  const [costPerUnit, setCostPerUnit] = useState("");
  // This sitting's saves only. Lives in the browser; every save is in the ledger.
  const [recorded, setRecorded] = useState<Recorded[]>([]);
  const nextId = useRef(1);

  const productRef = useRef<HTMLInputElement>(null);
  const cartonRef = useRef<HTMLInputElement>(null);
  const quantityRef = useRef<HTMLInputElement>(null);

  const selectedProduct = products.find((p) => p.productId === productId) ?? null;
  const unitLabel = selectedProduct?.unit === "UNIT" ? "units" : "kg";
  // Without a carton weight there is no nominal to compare against, so the
  // carton field would do nothing.
  const showCartons = selectedProduct?.cartonWeight != null;

  async function submit(prev: ReceiveStockState, formData: FormData): Promise<ReceiveStockState> {
    const submitted = products.find((p) => p.productId === Number(formData.get("productId")));
    const next = await receiveStockAction(prev, formData);
    if (next.result && submitted) {
      const result = next.result;
      setRecorded((list) => [
        {
          id: nextId.current++,
          name: submitted.name,
          unit: submitted.unit,
          actualQuantity: result.actualQuantity,
          gap: result.gap,
          nominal: result.nominal,
        },
        ...list,
      ]);
      // Ready for the next product. On errors nothing is cleared, so she can fix and resubmit.
      setProductId("");
      setQuery("");
      setCartonCount("");
      setActualQuantity("");
      setCostPerUnit("");
      // Refocus only where there is a physical keyboard. On a phone or tablet it
      // would raise the on-screen keyboard over the list she is reading.
      if (window.matchMedia("(pointer: fine)").matches) productRef.current?.focus();
    }
    return next;
  }

  const [state, formAction, pending] = useActionState(submit, initialState);

  const preview = useMemo(() => {
    if (!selectedProduct || selectedProduct.cartonWeight == null) return null;
    const cartons = Number(cartonCount);
    const actual = Number(actualQuantity);
    if (!cartonCount || !actualQuantity || Number.isNaN(cartons) || Number.isNaN(actual)) return null;
    const nominal = cartons * selectedProduct.cartonWeight;
    const gap = actual - nominal;
    return { nominal, gap };
  }, [selectedProduct, cartonCount, actualQuantity]);

  function focusAfterPick() {
    // The carton field only mounts once the product is set, so wait a frame.
    requestAnimationFrame(() => (cartonRef.current ?? quantityRef.current)?.focus());
  }

  return (
    <>
      <form action={formAction} className="flex flex-col gap-3 rounded-lg border border-ink/10 p-4">
        <input type="hidden" name="productId" value={productId} />
        <ProductCombobox
          products={products}
          query={query}
          onQueryChange={setQuery}
          selectedId={productId}
          onSelect={(id) => {
            setProductId(id);
            setCartonCount("");
            setCostPerUnit("");
          }}
          onPicked={focusAfterPick}
          inputRef={productRef}
        />

        {showCartons && (
          <label className="flex flex-col text-sm text-ink/70">
            Carton count (optional)
            <input
              ref={cartonRef}
              name="cartonCount"
              type="number"
              inputMode="numeric"
              step="1"
              min="1"
              value={cartonCount}
              onChange={(e) => setCartonCount(e.target.value)}
              className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
            />
          </label>
        )}

        <label className="flex flex-col text-sm text-ink/70">
          Actual {unitLabel} received
          <input
            ref={quantityRef}
            name="actualQuantity"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0.01"
            required
            value={actualQuantity}
            onChange={(e) => setActualQuantity(e.target.value)}
            className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
          />
        </label>

        <label className="flex flex-col text-sm text-ink/70">
          Cost this delivery, GH₵ per {selectedProduct?.unit === "UNIT" ? "unit" : "kg"} (optional)
          <input
            name="costPerUnit"
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0"
            value={costPerUnit}
            onChange={(e) => setCostPerUnit(e.target.value)}
            className="w-32 rounded border border-ink/20 bg-bg px-2 py-1"
          />
          {selectedProduct && (
            <span className="mt-1 text-xs text-ink/60">
              {selectedProduct.costPrice != null
                ? `Current cost GH₵ ${selectedProduct.costPrice.toFixed(2)}. Entering a cost replaces it.`
                : "No cost set yet. Entering one here sets it."}
            </span>
          )}
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

        {state.error && <p className="text-sm text-error">⚠ {state.error}</p>}
      </form>

      {recorded.length > 0 && (
        <section className="mt-6" aria-label="Recorded this session">
          <h2 className="mb-2 text-sm font-semibold text-ink">Recorded this session</h2>
          <ul className="flex flex-col divide-y divide-ink/10 rounded-lg border border-ink/10">
            {recorded.map((entry) => {
              const unit = entry.unit === "UNIT" ? "units" : "kg";
              return (
                <li key={entry.id} className="px-3 py-2 text-sm text-ink">
                  <span className="font-medium">{entry.name}</span> — {entry.actualQuantity} {unit}{" "}
                  received
                  {entry.gap != null && (
                    <span
                      className={`block text-xs ${entry.gap < 0 ? "text-alert-text" : "text-ink/60"}`}
                    >
                      {entry.gap < 0 ? "⚠ " : ""}
                      {Math.abs(entry.gap).toFixed(2)} {unit}{" "}
                      {entry.gap < 0 ? "short" : entry.gap > 0 ? "over" : "exact"} against nominal{" "}
                      {entry.nominal}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
          <p className="mt-2 text-xs text-ink/60">
            This list clears when you leave or refresh the page; every entry above is saved in the
            ledger. To correct a mistake, use{" "}
            <Link href="/owner/stock/reverse" className="underline">
              Reverse entry
            </Link>
            .
          </p>
        </section>
      )}
    </>
  );
}
