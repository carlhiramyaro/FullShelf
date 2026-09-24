import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { revokeDeviceAction } from "./actions";
import { GeneratePairingCodeButton } from "./GeneratePairingCodeButton";

type Device = {
  id: number;
  revoked: boolean;
};

async function fetchDevices(): Promise<Device[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/devices`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load devices: ${res.status}`);
  }
  return res.json();
}

export default async function DevicesPage() {
  const devices = await fetchDevices();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-ink">
        Shop devices
      </h1>

      <GeneratePairingCodeButton />

      <ul className="flex flex-col gap-3">
        {devices.map((device) => (
          <li
            key={device.id}
            className="flex items-center justify-between gap-3 rounded-lg border border-ink/10 p-4"
          >
            <p className="text-ink">
              Device #{device.id}
              {device.revoked && (
                <span className="ml-2 text-xs text-ink/50">revoked</span>
              )}
            </p>
            {!device.revoked && (
              <form action={revokeDeviceAction}>
                <input type="hidden" name="deviceId" value={device.id} />
                <button
                  type="submit"
                  className="rounded border border-error px-3 py-1 text-sm text-error hover:bg-error/10"
                >
                  Revoke
                </button>
              </form>
            )}
          </li>
        ))}
        {devices.length === 0 && (
          <p className="text-sm text-ink/50">No devices paired yet.</p>
        )}
      </ul>
    </div>
  );
}
