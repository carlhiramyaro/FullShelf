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

// What the product forms get back. A rejected save (duplicate name, bad
// price) comes back as an error to show inline, not a thrown error, which
// would replace the whole page with Next's 500 screen. `values` echoes what
// was typed so React's post-submit form reset doesn't wipe it.
export type ProductFormState = {
  error?: string;
  saved?: boolean;
  values?: Record<string, string>;
};

function productBody(formData: FormData) {
  const cartonWeight = formData.get("cartonWeight");
  return JSON.stringify({
    name: formData.get("name"),
    unit: formData.get("unit"),
    price: Number(formData.get("price")),
    alertLevel: Number(formData.get("alertLevel")),
    cartonWeight: cartonWeight ? Number(cartonWeight) : null,
  });
}

async function saveProduct(path: string, formData: FormData): Promise<ProductFormState> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: productBody(formData),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const values = Object.fromEntries(
      ["name", "unit", "price", "alertLevel", "cartonWeight"].map((k) => [k, String(formData.get(k) ?? "")]),
    );
    return { error: body?.message ?? "That product could not be saved", values };
  }
  revalidatePath("/owner/products");
  return { saved: true };
}

export async function createProductAction(
  _prevState: ProductFormState,
  formData: FormData,
): Promise<ProductFormState> {
  return saveProduct("/api/owner/products", formData);
}

export async function updateProductAction(
  _prevState: ProductFormState,
  formData: FormData,
): Promise<ProductFormState> {
  return saveProduct(`/api/owner/products/${formData.get("productId")}`, formData);
}

export async function deactivateProductAction(formData: FormData) {
  const productId = formData.get("productId");
  await ownerFetch(`/api/owner/products/${productId}/deactivate`, {
    method: "POST",
  });
  revalidatePath("/owner/products");
}
