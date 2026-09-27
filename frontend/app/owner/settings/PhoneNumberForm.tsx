"use client";

import { useActionState } from "react";
import { setPhoneNumberAction, PhoneNumberState } from "./actions";

const initialState: PhoneNumberState = {};

export default function PhoneNumberForm({
  currentPhoneNumber,
}: {
  currentPhoneNumber: string | null;
}) {
  const [state, formAction, pending] = useActionState(setPhoneNumberAction, initialState);

  return (
    <form action={formAction} className="flex flex-col gap-3 rounded-lg border border-ink/10 p-4">
      <label className="flex flex-col text-sm text-ink/70">
        Alert phone number
        <input
          name="phoneNumber"
          type="tel"
          placeholder="+233241234567"
          defaultValue={state.saved ?? currentPhoneNumber ?? ""}
          required
          className="w-64 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>

      <button
        type="submit"
        disabled={pending}
        className="mt-1 self-start rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Save number"}
      </button>

      {state.error && <p className="text-sm text-error">{state.error}</p>}
      {state.saved && <p className="text-sm text-primary">Saved: {state.saved}</p>}
    </form>
  );
}
