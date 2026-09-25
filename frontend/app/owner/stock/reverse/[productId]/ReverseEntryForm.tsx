"use client";

import { useActionState, useState } from "react";
import { reverseEntryAction, ReverseStockState } from "../actions";

type Movement = {
  movementId: number;
  type: "OPENING" | "RECEIVED";
  quantity: number;
  note: string | null;
  createdAt: string;
};

const initialState: ReverseStockState = {};

export default function ReverseEntryForm({
  productId,
  movements,
}: {
  productId: number;
  movements: Movement[];
}) {
  const [state, formAction, pending] = useActionState(reverseEntryAction, initialState);
  const [movementId, setMovementId] = useState<number | "">("");

  return (
    <form action={formAction} className="flex flex-col gap-3 rounded-lg border border-ink/10 p-4">
      <input type="hidden" name="productId" value={productId} />

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 text-sm text-ink/70">Which entry was mistyped?</legend>
        {movements.map((movement) => (
          <label key={movement.movementId} className="flex items-start gap-2 text-sm text-ink/80">
            <input
              type="radio"
              name="movementId"
              value={movement.movementId}
              checked={movementId === movement.movementId}
              onChange={() => setMovementId(movement.movementId)}
              className="mt-1"
              required
            />
            <span>
              {movement.type === "OPENING" ? "Opening" : "Received"} — {movement.quantity}
              {movement.note && <span className="text-ink/50"> ({movement.note})</span>}
              <br />
              <span className="text-xs text-ink/40">{new Date(movement.createdAt).toLocaleString()}</span>
            </span>
          </label>
        ))}
      </fieldset>

      <label className="flex flex-col text-sm text-ink/70">
        Note (optional — why this was wrong)
        <textarea name="note" rows={2} className="rounded border border-ink/20 bg-bg px-2 py-1" />
      </label>

      <button
        type="submit"
        disabled={pending || movementId === ""}
        className="mt-1 self-start rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Reversing…" : "Reverse this entry"}
      </button>

      {state.error && <p className="text-sm text-error">{state.error}</p>}
      {state.result && (
        <p className="text-sm text-primary">
          Reversed. Go to Opening stock or Receive stock to re-enter the correct figure.
        </p>
      )}
    </form>
  );
}
