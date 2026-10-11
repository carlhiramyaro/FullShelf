"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

// A rejected save comes back as an error to show inline (a thrown error would
// replace the page). `values` echoes what was typed, since React resets the
// form after every submit.
export type AdminFormState = {
  error?: string;
  saved?: boolean;
  values?: Record<string, string>;
};

const OWNER_FIELDS = ["ownerName", "phone", "email"];

async function post(path: string, formData: FormData, fields: string[]): Promise<AdminFormState> {
  const { getToken } = await auth();
  const token = await getToken();
  const body = Object.fromEntries(fields.map((k) => [k, String(formData.get(k) ?? "")]));
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => null);
    return { error: err?.message ?? "That could not be saved", values: body };
  }
  revalidatePath("/admin");
  return { saved: true };
}

export async function createShopAction(_prev: AdminFormState, formData: FormData): Promise<AdminFormState> {
  return post("/api/admin/shops", formData, ["shopName", ...OWNER_FIELDS]);
}

export async function relinkOwnerAction(_prev: AdminFormState, formData: FormData): Promise<AdminFormState> {
  return post(`/api/admin/shops/${formData.get("shopId")}/owner`, formData, OWNER_FIELDS);
}
