import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { formatCash } from "@/lib/format";
import StatTile from "../_components/StatTile";
import ExpenseForm from "./ExpenseForm";
import VoidExpenseForm from "./VoidExpenseForm";
import { paidFromLabel } from "./categories";

type Expense = {
  id: number;
  expenseDate: string;
  amount: number;
  categoryLabel: string;
  paidFrom: "CASH" | "MOMO" | "OTHER";
  note: string | null;
  createdByName: string;
  voided: boolean;
  voidNote: string | null;
};

type ExpenseList = {
  from: string;
  to: string;
  expenses: Expense[];
  total: number;
  paidFromCash: number;
  paidFromMomo: number;
  paidFromOther: number;
  byCategory: { category: string; label: string; amount: number }[];
};

async function fetchExpenses(from?: string, to?: string): Promise<ExpenseList> {
  const { getToken } = await auth();
  const token = await getToken();
  const query = new URLSearchParams();
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const res = await fetch(`${API_BASE_URL}/api/owner/expenses?${query}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load expenses: ${res.status}`);
  }
  return res.json();
}

// Same "server clock = Accra" simplification as Day close (UTC, no DST).
function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

// No range given means month to date; the backend picks it and echoes it back, so the
// filter below always shows what the list is really covering.
export default async function ExpensesPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const { from, to } = await searchParams;
  const list = await fetchExpenses(from, to);

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Expenses</h1>
      <p className="mb-6 text-sm text-ink/60">
        Money the shop spent. Cash expenses reduce expected cash and MoMo expenses reduce expected
        MoMo; &quot;Other&quot; counts toward profit only. A wrong one is voided, not edited.
      </p>

      <div className="mb-8">
        <ExpenseForm today={todayIsoDate()} />
      </div>

      <form className="mb-6 flex items-end gap-3">
        <label className="flex flex-col text-sm text-ink/70">
          From
          <input
            type="date"
            name="from"
            defaultValue={list.from}
            className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink"
          />
        </label>
        <label className="flex flex-col text-sm text-ink/70">
          To
          <input
            type="date"
            name="to"
            defaultValue={list.to}
            className="mt-1 rounded-lg border border-ink/20 px-3 py-2 text-ink"
          />
        </label>
        <button type="submit" className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-dark">
          View
        </button>
      </form>

      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatTile
          label="Total expenses"
          value={formatCash(list.total)}
          hint={`Cash ${formatCash(list.paidFromCash)} · MoMo ${formatCash(list.paidFromMomo)} · Other ${formatCash(list.paidFromOther)}`}
        />
        <div className="rounded-lg border border-ink/10 bg-bg p-4">
          <p className="text-xs text-ink/60">By category</p>
          {list.byCategory.length === 0 ? (
            <p className="mt-1 text-sm text-ink/50">None in this range.</p>
          ) : (
            <ul className="mt-1 flex flex-col gap-0.5 text-sm text-ink">
              {list.byCategory.map((c) => (
                <li key={c.category} className="flex justify-between">
                  <span>{c.label}</span>
                  <span>{formatCash(c.amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {list.expenses.length === 0 ? (
        <p className="text-sm text-ink/50">No expenses in this range.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {list.expenses.map((e) => (
            <li key={e.id} className="flex items-start justify-between gap-3 rounded-lg border border-ink/10 p-3">
              <span className="min-w-0">
                <span className={`font-medium ${e.voided ? "text-ink/50 line-through" : "text-ink"}`}>
                  {e.categoryLabel}
                </span>{" "}
                <span className="text-xs text-ink/50">
                  {e.expenseDate} · {paidFromLabel(e.paidFrom)} · {e.createdByName}
                </span>
                {e.voided && <span className="ml-2 text-xs font-medium text-error">⚠ Voided</span>}
                {e.note && <span className="block text-sm text-ink/70">{e.note}</span>}
                {e.voided && e.voidNote && (
                  <span className="block text-xs text-ink/50">Void note: {e.voidNote}</span>
                )}
              </span>
              <span className="flex shrink-0 flex-col items-end gap-2">
                <span className={`font-medium ${e.voided ? "text-ink/50 line-through" : "text-ink"}`}>
                  {formatCash(e.amount)}
                </span>
                {!e.voided && <VoidExpenseForm expenseId={e.id} />}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
