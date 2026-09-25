"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

export type ReverseStockState = {
  error?: string;
  result?: {
    movementId: number;
    type: string;
    quantity: number;
  };
};

export async function reverseEntryAction(
  _prevState: ReverseStockState,
  formData: FormData,
): Promise<ReverseStockState> {
  const productId = Number(formData.get("productId"));
  const movementIdRaw = String(formData.get("movementId") ?? "").trim();
  const note = String(formData.get("note") ?? "").trim();

  if (movementIdRaw === "") {
    return { error: "Pick which entry was mistyped" };
  }

  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/reverse`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      movementId: Number(movementIdRaw),
      note: note === "" ? null : note,
    }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That entry could not be reversed" };
  }

  const result = await res.json();
  if (productId) {
    revalidatePath(`/owner/stock/reverse/${productId}`);
  }
  revalidatePath("/owner/stock/reverse");
  return {
    result: {
      movementId: result.movementId,
      type: result.type,
      quantity: result.quantity,
    },
  };
}
