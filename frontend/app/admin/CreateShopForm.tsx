"use client";

import { useActionState } from "react";
import { createShopAction, AdminFormState } from "./actions";
import OwnerFields from "./OwnerFields";

export default function CreateShopForm() {
  const [state, formAction, pending] = useActionState(createShopAction, {} as AdminFormState);
  return (
    <form action={formAction} className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col text-sm text-ink/70">
        Shop name
        <input
          name="shopName"
          required
          defaultValue={state.values?.shopName ?? ""}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <OwnerFields state={state} />
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-primary px-4 py-1.5 text-sm font-medium text-on-dark disabled:opacity-60"
      >
        {pending ? "Creating…" : "Create shop"}
      </button>
      {state.error && <p className="w-full text-sm text-error">⚠ {state.error}</p>}
      {state.saved && <p className="w-full text-sm text-primary">✓ Shop created. The owner can now sign in.</p>}
    </form>
  );
}
