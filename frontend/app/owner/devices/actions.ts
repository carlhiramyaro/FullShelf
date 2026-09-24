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

export async function generatePairingCodeAction(): Promise<string> {
  const res = await ownerFetch("/api/owner/devices/pairing-code", {
    method: "POST",
  });
  const { code } = (await res.json()) as { code: string };
  revalidatePath("/owner/devices");
  return code;
}

export async function revokeDeviceAction(formData: FormData) {
  const deviceId = formData.get("deviceId");
  await ownerFetch(`/api/owner/devices/${deviceId}/revoke`, {
    method: "POST",
  });
  revalidatePath("/owner/devices");
}
