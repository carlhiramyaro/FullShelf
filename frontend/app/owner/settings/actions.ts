"use server";

import { auth } from "@clerk/nextjs/server";
import { revalidatePath } from "next/cache";
import { API_BASE_URL } from "@/lib/api";

export type PhoneNumberState = {
  error?: string;
  saved?: string;
};

export async function setPhoneNumberAction(
  _prevState: PhoneNumberState,
  formData: FormData,
): Promise<PhoneNumberState> {
  const phoneNumber = String(formData.get("phoneNumber") ?? "").trim();

  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/me/phone`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ phoneNumber }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    return { error: body?.message ?? "That phone number could not be saved" };
  }

  const result = await res.json();
  revalidatePath("/owner/settings");
  return { saved: result.phoneNumber };
}
