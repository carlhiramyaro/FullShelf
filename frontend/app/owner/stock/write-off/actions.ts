"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

export type WriteOffState = {
  error?: string;
  result?: {
    quantity: number;
    note: string;
  };
};

export async function writeOffAction(
  _prevState: WriteOffState,
  formData: FormData,
): Promise<WriteOffState> {
  const productId = Number(formData.get("productId"));
  const quantityRaw = String(formData.get("quantity") ?? "").trim();
  const note = String(formData.get("note") ?? "").trim();

  if (!productId || quantityRaw === "" || note === "") {
    return { error: "Pick a product, enter a quantity and a note" };
  }

  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/write-off`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      productId,
      quantity: Number(quantityRaw),
      note,
    }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That write-off could not be saved" };
  }

  const result = await res.json();
  revalidatePath("/owner/stock/write-off");
  return {
    result: {
      quantity: result.quantity,
      note: result.note,
    },
  };
}
