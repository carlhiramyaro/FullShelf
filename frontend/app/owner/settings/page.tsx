import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import PhoneNumberForm from "./PhoneNumberForm";

type OwnerProfile = {
  id: number;
  name: string;
  role: string;
  phoneNumber: string | null;
};

async function fetchOwnerProfile(): Promise<OwnerProfile> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/me`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load owner profile: ${res.status}`);
  }
  return res.json();
}

export default async function SettingsPage() {
  const owner = await fetchOwnerProfile();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-semibold text-ink">Settings</h1>
      <p className="mb-6 text-sm text-ink/60">
        Low-stock alerts are sent by SMS to this number, in international format (e.g. +233241234567).
      </p>

      <PhoneNumberForm currentPhoneNumber={owner.phoneNumber} />
    </div>
  );
}
