import { auth } from "@clerk/nextjs/server";
import { API_BASE_URL } from "@/lib/api";
import { createStaffAction, deactivateStaffAction, resetPinAction } from "./actions";

type Staff = {
  id: number;
  name: string;
  active: boolean;
  pinLocked: boolean;
};

async function fetchStaff(): Promise<Staff[]> {
  const { getToken } = await auth();
  const token = await getToken();
  const res = await fetch(`${API_BASE_URL}/api/owner/staff`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`Failed to load staff: ${res.status}`);
  }
  return res.json();
}

export default async function StaffPage() {
  const staff = await fetchStaff();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
        Staff
      </h1>

      <form
        action={createStaffAction}
        className="mb-8 flex flex-wrap items-end gap-3 rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-950"
      >
        <label className="flex flex-col text-sm text-zinc-600 dark:text-zinc-400">
          Name
          <input
            name="name"
            required
            className="rounded border border-zinc-300 px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
          />
        </label>
        <label className="flex flex-col text-sm text-zinc-600 dark:text-zinc-400">
          4-digit PIN
          <input
            name="pin"
            required
            maxLength={4}
            pattern="\d{4}"
            inputMode="numeric"
            className="rounded border border-zinc-300 px-2 py-1 dark:border-zinc-700 dark:bg-zinc-900"
          />
        </label>
        <button
          type="submit"
          className="rounded bg-zinc-900 px-4 py-1.5 text-sm text-white dark:bg-zinc-50 dark:text-zinc-900"
        >
          Add staff
        </button>
      </form>

      <ul className="flex flex-col gap-3">
        {staff.map((member) => (
          <li
            key={member.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-950"
          >
            <div>
              <p className="font-medium text-zinc-900 dark:text-zinc-50">
                {member.name}
                {!member.active && (
                  <span className="ml-2 text-xs text-zinc-500">deactivated</span>
                )}
                {member.pinLocked && (
                  <span className="ml-2 text-xs text-red-600">PIN locked</span>
                )}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <form action={resetPinAction} className="flex items-center gap-2">
                <input type="hidden" name="staffId" value={member.id} />
                <input
                  name="pin"
                  placeholder="New PIN"
                  required
                  maxLength={4}
                  pattern="\d{4}"
                  inputMode="numeric"
                  className="w-24 rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-zinc-900"
                />
                <button
                  type="submit"
                  className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
                >
                  Reset PIN
                </button>
              </form>
              {member.active && (
                <form action={deactivateStaffAction}>
                  <input type="hidden" name="staffId" value={member.id} />
                  <button
                    type="submit"
                    className="rounded border border-red-300 px-3 py-1 text-sm text-red-700 dark:border-red-900 dark:text-red-400"
                  >
                    Deactivate
                  </button>
                </form>
              )}
            </div>
          </li>
        ))}
        {staff.length === 0 && (
          <p className="text-sm text-zinc-500">No staff yet.</p>
        )}
      </ul>
    </div>
  );
}
