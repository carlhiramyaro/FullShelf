"use client";

import { useActionState, useState } from "react";
import { voidSaleAction, VoidSaleState } from "../actions";

const initialState: VoidSaleState = {};

export default function VoidSaleForm({ receiptNumber }: { receiptNumber: number }) {
  const [state, formAction, pending] = useActionState(voidSaleAction, initialState);
  const [confirming, setConfirming] = useState(false);

  if (state.voided) {
    return <p className="text-sm text-error">Sale voided. Stock has been restored.</p>;
  }

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className="rounded border border-error px-4 py-1.5 text-sm text-error hover:bg-error/10"
      >
        Void sale
      </button>
    );
  }

  return (
    <form action={formAction} className="flex flex-col gap-3 rounded-lg border border-error/30 p-4">
      <input type="hidden" name="receiptNumber" value={receiptNumber} />
      <p className="text-sm text-ink">
        This cancels the sale, restores the stock it sold, and reduces expected cash. The
        receipt stays visible, marked voided.
      </p>

      <label className="flex flex-col text-sm text-ink/70">
        Note (optional)
        <textarea name="note" rows={2} className="rounded border border-ink/20 bg-bg px-2 py-1" />
      </label>

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-error px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
        >
          {pending ? "Voiding…" : "Confirm void"}
        </button>
        <button
          type="button"
          onClick={() => setConfirming(false)}
          disabled={pending}
          className="rounded px-4 py-1.5 text-sm text-ink/60 hover:bg-ink/5"
        >
          Cancel
        </button>
      </div>

      {state.error && <p className="text-sm text-error">{state.error}</p>}
    </form>
  );
}
