"use client";

import { useActionState, useState } from "react";
import { voidExpenseAction, VoidExpenseState } from "./actions";

const initialState: VoidExpenseState = {};

export default function VoidExpenseForm({ expenseId }: { expenseId: number }) {
  const [state, formAction, pending] = useActionState(voidExpenseAction, initialState);
  const [confirming, setConfirming] = useState(false);

  if (state.voided) {
    return <p className="text-xs text-error">Voided.</p>;
  }

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className="rounded border border-error px-3 py-1 text-xs text-error hover:bg-error/10"
      >
        Void
      </button>
    );
  }

  return (
    <form action={formAction} className="flex flex-col gap-2 rounded-lg border border-error/30 p-3">
      <input type="hidden" name="expenseId" value={expenseId} />
      <p className="text-xs text-ink">
        This stops the expense counting. It stays in the list, marked voided.
      </p>
      <label className="flex flex-col text-xs text-ink/70">
        Note (optional)
        <input name="note" className="rounded border border-ink/20 bg-bg px-2 py-1 text-sm" />
      </label>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={pending}
          className="rounded bg-error px-3 py-1 text-xs text-on-dark disabled:opacity-50"
        >
          {pending ? "Voiding…" : "Confirm void"}
        </button>
        <button
          type="button"
          onClick={() => setConfirming(false)}
          disabled={pending}
          className="rounded px-3 py-1 text-xs text-ink/60 hover:bg-ink/5"
        >
          Cancel
        </button>
      </div>
      {state.error && (
        <p role="alert" className="text-xs text-error">
          ⚠ {state.error}
        </p>
      )}
    </form>
  );
}
