"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

export type VoidSaleState = {
  error?: string;
  voided?: boolean;
};

export async function voidSaleAction(
  _prevState: VoidSaleState,
  formData: FormData,
): Promise<VoidSaleState> {
  const receiptNumber = String(formData.get("receiptNumber") ?? "").trim();
  const note = String(formData.get("note") ?? "").trim();

  if (receiptNumber === "") {
    return { error: "Missing receipt number" };
  }

  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/sales/${receiptNumber}/void`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ note: note === "" ? null : note }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That sale could not be voided" };
  }

  revalidatePath(`/owner/sales/${receiptNumber}`);
  revalidatePath("/owner/sales");
  return { voided: true };
}
