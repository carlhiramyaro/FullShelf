"use client";

import { useActionState, useState } from "react";
import { addExpenseAction, ExpenseFormState } from "./actions";
import { CATEGORIES, PAID_FROM } from "./categories";

const initialState: ExpenseFormState = {};

// `today` comes from the server (UTC date) so the picker stops at today, as the
// backend does. A rejected save keeps what was typed via the state's values.
export default function ExpenseForm({ today }: { today: string }) {
  const [state, formAction, pending] = useActionState(addExpenseAction, initialState);
  const v = state.values;
  const [category, setCategory] = useState(v?.category ?? "RENT");

  return (
    <form action={formAction} className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col text-sm text-ink/70">
        Date
        <input
          name="expenseDate"
          type="date"
          required
          max={today}
          defaultValue={v?.expenseDate ?? today}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Amount (GH₵)
        <input
          name="amount"
          type="number"
          step="0.01"
          min="0.01"
          required
          defaultValue={v?.amount ?? ""}
          className="w-28 rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Category
        <select
          name="category"
          required
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        >
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col text-sm text-ink/70">
        Paid from
        <select
          name="paidFrom"
          required
          defaultValue={v?.paidFrom ?? "CASH"}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        >
          {PAID_FROM.map((p) => (
            <option key={p.value} value={p.value}>
              {p.label}
            </option>
          ))}
        </select>
      </label>
      <label className="flex min-w-48 flex-1 flex-col text-sm text-ink/70">
        Note{category === "OTHER" ? " (required for Other)" : " (optional)"}
        <input
          name="note"
          required={category === "OTHER"}
          defaultValue={v?.note ?? ""}
          className="rounded border border-ink/20 bg-bg px-2 py-1"
        />
      </label>
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-primary px-4 py-1.5 text-sm text-on-dark disabled:opacity-50"
      >
        {pending ? "Saving…" : "Add expense"}
      </button>
      {state.error && (
        <p role="alert" className="basis-full text-sm text-error">
          ⚠ {state.error}
        </p>
      )}
      {state.saved && <p className="basis-full text-sm text-primary">✓ Expense saved.</p>}
    </form>
  );
}
