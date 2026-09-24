"use client";

import { useActionState } from "react";
import { generatePairingCodeAction } from "./actions";

// The Server Action needs to hand a value back to display (the raw code,
// shown once) rather than just redirect/revalidate, so it's driven through
// useActionState instead of a plain <form action={...}> like the other
// owner mutations.
async function action(): Promise<string> {
  return generatePairingCodeAction();
}

export function GeneratePairingCodeButton() {
  const [code, formAction, pending] = useActionState(action, null);

  return (
    <form
      action={formAction}
      className="mb-8 flex flex-col items-start gap-3 rounded-lg border border-ink/10 p-4"
    >
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Generating…" : "Generate pairing code"}
      </button>
      {code && (
        <p className="text-sm text-ink/80">
          Code:{" "}
          <span className="font-mono text-lg font-semibold text-primary">
            {code}
          </span>{" "}
          — enter this on the shop device within 10 minutes.
        </p>
      )}
    </form>
  );
}
