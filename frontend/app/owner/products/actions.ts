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

export async function createProductAction(formData: FormData) {
  await ownerFetch("/api/owner/products", {
    method: "POST",
    body: productBody(formData),
  });
  revalidatePath("/owner/products");
}

export async function updateProductAction(formData: FormData) {
  const productId = formData.get("productId");
  await ownerFetch(`/api/owner/products/${productId}`, {
    method: "POST",
    body: productBody(formData),
  });
  revalidatePath("/owner/products");
}

export async function deactivateProductAction(formData: FormData) {
  const productId = formData.get("productId");
  await ownerFetch(`/api/owner/products/${productId}/deactivate`, {
    method: "POST",
  });
  revalidatePath("/owner/products");
}
