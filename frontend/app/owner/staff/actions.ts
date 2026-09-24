"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

// Owner mutations go through Server Actions rather than client-side fetch:
// they run on the Next.js server, so calling the Spring backend is a plain
// server-to-server request authenticated with the same Clerk session token
// the browser already has — no CORS, no token handling in the browser.
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

export async function createStaffAction(formData: FormData) {
  const name = formData.get("name");
  const pin = formData.get("pin");
  await ownerFetch("/api/owner/staff", {
    method: "POST",
    body: JSON.stringify({ name, pin }),
  });
  revalidatePath("/owner/staff");
}

export async function resetPinAction(formData: FormData) {
  const staffId = formData.get("staffId");
  const pin = formData.get("pin");
  await ownerFetch(`/api/owner/staff/${staffId}/reset-pin`, {
    method: "POST",
    body: JSON.stringify({ pin }),
  });
  revalidatePath("/owner/staff");
}

export async function deactivateStaffAction(formData: FormData) {
  const staffId = formData.get("staffId");
  await ownerFetch(`/api/owner/staff/${staffId}/deactivate`, {
    method: "POST",
  });
  revalidatePath("/owner/staff");
}
