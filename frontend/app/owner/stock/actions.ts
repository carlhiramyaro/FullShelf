"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

async function ownerFetch(path: string, init: RequestInit = {}) {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      ...init.headers,
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });
  if (!res.ok) {
    throw new Error(`Request to ${path} failed: ${res.status}`);
  }
  return res;
}

export async function setOpeningStockAction(formData: FormData) {
  const entries = Array.from(formData.entries())
    .filter(([key, value]) => key.startsWith("qty-") && String(value).trim() !== "")
    .map(([key, value]) => ({
      productId: Number(key.slice("qty-".length)),
      quantity: Number(value),
    }));

  if (entries.length === 0) {
    return;
  }

  await ownerFetch("/api/owner/stock/opening", {
    method: "POST",
    body: JSON.stringify({ entries }),
  });
  revalidatePath("/owner/stock");
}
