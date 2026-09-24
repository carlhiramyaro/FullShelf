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
      className="mb-8 flex flex-col items-start gap-3 rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-950"
    >
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-zinc-900 px-4 py-1.5 text-sm text-white disabled:opacity-50 dark:bg-zinc-50 dark:text-zinc-900"
      >
        {pending ? "Generating…" : "Generate pairing code"}
      </button>
      {code && (
        <p className="text-sm text-zinc-700 dark:text-zinc-300">
          Code: <span className="font-mono text-lg font-semibold">{code}</span>{" "}
          — enter this on the shop device within 10 minutes.
        </p>
      )}
    </form>
  );
}
