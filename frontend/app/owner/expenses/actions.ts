"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

// A rejected save comes back as an error to show inline, not a thrown error (which
// would replace the page). `values` echoes what was typed so React's post-submit
// form reset doesn't wipe it, same as the Products form.
export type ExpenseFormState = {
  error?: string;
  saved?: boolean;
  values?: Record<string, string>;
};

export type VoidExpenseState = {
  error?: string;
  voided?: boolean;
};

async function post(path: string, body: unknown) {
  const { getToken } = await auth();
  const token = await getToken();
  return fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function addExpenseAction(
  _prev: ExpenseFormState,
  formData: FormData,
): Promise<ExpenseFormState> {
  const fields = ["expenseDate", "amount", "category", "paidFrom", "note"];
  const values = Object.fromEntries(fields.map((k) => [k, String(formData.get(k) ?? "")]));
  const note = values.note.trim();

  const res = await post("/api/owner/expenses", {
    expenseDate: values.expenseDate === "" ? null : values.expenseDate,
    amount: values.amount === "" ? null : Number(values.amount),
    category: values.category,
    paidFrom: values.paidFrom,
    note: note === "" ? null : note,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That expense could not be saved", values };
  }
  revalidatePath("/owner/expenses");
  revalidatePath("/owner");
  revalidatePath("/owner/day-close");
  return { saved: true };
}

export async function voidExpenseAction(
  _prev: VoidExpenseState,
  formData: FormData,
): Promise<VoidExpenseState> {
  const id = String(formData.get("expenseId") ?? "").trim();
  const note = String(formData.get("note") ?? "").trim();
  if (id === "") {
    return { error: "Missing expense" };
  }
  const res = await post(`/api/owner/expenses/${id}/void`, { note: note === "" ? null : note });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That expense could not be voided" };
  }
  revalidatePath("/owner/expenses");
  revalidatePath("/owner");
  revalidatePath("/owner/day-close");
  return { voided: true };
}
