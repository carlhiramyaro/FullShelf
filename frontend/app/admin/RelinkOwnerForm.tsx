"use client";

import { useActionState, useState } from "react";
import { relinkOwnerAction, AdminFormState } from "./actions";
import OwnerFields from "./OwnerFields";

// Two-step reveal (same idea as VoidSaleForm): re-linking locks the current
// owner out at once, so it sits behind an explicit warning.
export default function RelinkOwnerForm({ shopId, hasActiveOwner }: { shopId: number; hasActiveOwner: boolean }) {
  const [open, setOpen] = useState(false);
  const [state, formAction, pending] = useActionState(relinkOwnerAction, {} as AdminFormState);

  if (!open) {
    return (
      <div>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="rounded border border-secondary px-3 py-1 text-sm text-secondary hover:bg-secondary/10"
        >
          Re-link owner
        </button>
        {state.saved && <p className="mt-2 text-sm text-primary">✓ Owner updated.</p>}
      </div>
    );
  }

  return (
    <form action={formAction} className="flex flex-wrap items-end gap-3">
      <input type="hidden" name="shopId" value={shopId} />
      {hasActiveOwner && (
        <p className="w-full text-sm text-alert-text">
          ⚠ The current owner is signed out of this shop as soon as you confirm, and their phone number is cleared.
        </p>
      )}
      <OwnerFields state={state} />
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-primary px-4 py-1.5 text-sm font-medium text-on-dark disabled:opacity-60"
      >
        {pending ? "Saving…" : "Confirm re-link"}
      </button>
      <button type="button" onClick={() => setOpen(false)} className="text-sm text-secondary underline">
        Cancel
      </button>
      {state.error && <p className="w-full text-sm text-error">⚠ {state.error}</p>}
    </form>
  );
}
