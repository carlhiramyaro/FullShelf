"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

export type ReceiveStockState = {
  error?: string;
  result?: {
    actualQuantity: number;
    nominal: number | null;
    gap: number | null;
  };
};

export async function receiveStockAction(
  _prevState: ReceiveStockState,
  formData: FormData,
): Promise<ReceiveStockState> {
  const productId = Number(formData.get("productId"));
  const cartonCountRaw = String(formData.get("cartonCount") ?? "").trim();
  const actualQuantityRaw = String(formData.get("actualQuantity") ?? "").trim();

  if (!productId || actualQuantityRaw === "") {
    return { error: "Pick a product and enter the actual quantity received" };
  }

  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/stock/receive`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      productId,
      cartonCount: cartonCountRaw === "" ? null : Number(cartonCountRaw),
      actualQuantity: Number(actualQuantityRaw),
    }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That delivery could not be saved" };
  }

  const result = await res.json();
  revalidatePath("/owner/stock/receive");
  return {
    result: {
      actualQuantity: result.actualQuantity,
      nominal: result.nominal,
      gap: result.gap,
    },
  };
}
