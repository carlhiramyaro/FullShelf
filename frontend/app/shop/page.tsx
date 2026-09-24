"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { API_BASE_URL } from "@/lib/api";

const DEVICE_TOKEN_KEY = "fullshelf_device_token";
const STAFF_SESSION_KEY = "fullshelf_staff_session";
const IDLE_LOCK_MS = 2 * 60 * 1000;

type RosterEntry = { id: number; name: string };

type Screen =
  | { name: "pairing" }
  | { name: "roster" }
  | { name: "pin"; staff: RosterEntry }
  | { name: "serving"; staff: RosterEntry };

async function errorMessage(res: Response, fallback: string): Promise<string> {
  const body = await res.json().catch(() => null);
  return (body as { message?: string } | null)?.message ?? fallback;
}

export default function ShopPage() {
  const [screen, setScreen] = useState<Screen>({ name: "pairing" });
  const [roster, setRoster] = useState<RosterEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Any invalid/missing/revoked device token routes back here — this is
  // what makes an unpaired (or unpaired-again) browser show nothing but the
  // pairing gate, matching mvp.md's "unpaired browsers show owner login
  // only" (the owner still signs in separately, at /sign-in). Used by event
  // handlers (pairing, login, logout), where a synchronous state reset is
  // fine — the initial mount check below is split out so it never sets
  // state before its own fetch resolves, which react-hooks' rules require
  // of effects.
  const loadRoster = useCallback(async () => {
    const deviceToken = localStorage.getItem(DEVICE_TOKEN_KEY);
    if (!deviceToken) {
      setScreen({ name: "pairing" });
      return;
    }
    const res = await fetch(`${API_BASE_URL}/api/staff/roster`, {
      headers: { "X-Device-Token": deviceToken },
    });
    if (!res.ok) {
      localStorage.removeItem(DEVICE_TOKEN_KEY);
      setScreen({ name: "pairing" });
      return;
    }
    setRoster(await res.json());
    setScreen({ name: "roster" });
  }, []);

  useEffect(() => {
    const deviceToken = localStorage.getItem(DEVICE_TOKEN_KEY);
    if (!deviceToken) {
      return; // already showing the pairing screen, the initial state
    }
    fetch(`${API_BASE_URL}/api/staff/roster`, {
      headers: { "X-Device-Token": deviceToken },
    }).then(async (res) => {
      if (!res.ok) {
        localStorage.removeItem(DEVICE_TOKEN_KEY);
        setScreen({ name: "pairing" });
        return;
      }
      setRoster(await res.json());
      setScreen({ name: "roster" });
    });
  }, []);

  async function handlePair(code: string) {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE_URL}/api/devices/pair`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code }),
      });
      if (!res.ok) {
        throw new Error(await errorMessage(res, "Invalid code"));
      }
      const { deviceToken } = (await res.json()) as { deviceToken: string };
      localStorage.setItem(DEVICE_TOKEN_KEY, deviceToken);
      await loadRoster();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Pairing failed");
    } finally {
      setBusy(false);
    }
  }

  async function handleLogin(staff: RosterEntry, pin: string) {
    setBusy(true);
    setError(null);
    try {
      const deviceToken = localStorage.getItem(DEVICE_TOKEN_KEY);
      if (!deviceToken) {
        setScreen({ name: "pairing" });
        return;
      }
      const res = await fetch(`${API_BASE_URL}/api/staff/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Device-Token": deviceToken,
        },
        body: JSON.stringify({ staffId: staff.id, pin }),
      });
      if (!res.ok) {
        throw new Error(await errorMessage(res, "Incorrect PIN"));
      }
      const { sessionToken } = (await res.json()) as { sessionToken: string };
      sessionStorage.setItem(STAFF_SESSION_KEY, sessionToken);
      setScreen({ name: "serving", staff });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  // Shared by the idle-lock timeout and the explicit "Switch user" tap —
  // mvp.md treats them as the same event (drop back to the tap-name screen).
  const handleLogout = useCallback(async () => {
    const sessionToken = sessionStorage.getItem(STAFF_SESSION_KEY);
    sessionStorage.removeItem(STAFF_SESSION_KEY);
    if (sessionToken) {
      await fetch(`${API_BASE_URL}/api/staff/logout`, {
        method: "POST",
        headers: { "X-Staff-Session-Token": sessionToken },
      }).catch(() => {});
    }
    setError(null);
    await loadRoster();
  }, [loadRoster]);

  const idleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (screen.name !== "serving") return;

    const resetTimer = () => {
      if (idleTimer.current) clearTimeout(idleTimer.current);
      idleTimer.current = setTimeout(handleLogout, IDLE_LOCK_MS);
    };
    resetTimer();

    const events = ["pointerdown", "keydown", "touchstart"] as const;
    events.forEach((event) => window.addEventListener(event, resetTimer));
    return () => {
      if (idleTimer.current) clearTimeout(idleTimer.current);
      events.forEach((event) => window.removeEventListener(event, resetTimer));
    };
  }, [screen.name, handleLogout]);

  if (screen.name === "pairing") {
    return <PairingForm onSubmit={handlePair} busy={busy} error={error} />;
  }
  if (screen.name === "roster") {
    return (
      <RosterScreen
        roster={roster}
        onSelect={(staff) => {
          setError(null);
          setScreen({ name: "pin", staff });
        }}
      />
    );
  }
  if (screen.name === "pin") {
    return (
      <PinPad
        staff={screen.staff}
        busy={busy}
        error={error}
        onSubmit={(pin) => handleLogin(screen.staff, pin)}
        onBack={() => {
          setError(null);
          setScreen({ name: "roster" });
        }}
      />
    );
  }
  return <ServingScreen staff={screen.staff} onSwitchUser={handleLogout} />;
}

function PairingForm({
  onSubmit,
  busy,
  error,
}: {
  onSubmit: (code: string) => void;
  busy: boolean;
  error: string | null;
}) {
  const [code, setCode] = useState("");

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 p-6 dark:bg-black">
      <form
        className="flex w-full max-w-sm flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-8 dark:border-zinc-800 dark:bg-zinc-950"
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit(code.trim().toUpperCase());
        }}
      >
        <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">
          Pair this device
        </h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Enter the code Aunt Amerley generated on her phone.
        </p>
        <input
          className="rounded border border-zinc-300 px-3 py-2 text-center text-lg tracking-widest uppercase dark:border-zinc-700 dark:bg-zinc-900"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          maxLength={6}
          placeholder="XXXXXX"
          autoFocus
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={busy || code.length === 0}
          className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-50 dark:bg-zinc-50 dark:text-zinc-900"
        >
          {busy ? "Pairing…" : "Pair device"}
        </button>
      </form>
    </div>
  );
}

function RosterScreen({
  roster,
  onSelect,
}: {
  roster: RosterEntry[];
  onSelect: (staff: RosterEntry) => void;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-zinc-50 p-6 dark:bg-black">
      <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">
        Who&apos;s serving?
      </h1>
      <div className="grid w-full max-w-md grid-cols-2 gap-4">
        {roster.map((staff) => (
          <button
            key={staff.id}
            onClick={() => onSelect(staff)}
            className="rounded-lg border border-zinc-200 bg-white p-6 text-lg font-medium text-zinc-900 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50 dark:hover:bg-zinc-900"
          >
            {staff.name}
          </button>
        ))}
        {roster.length === 0 && (
          <p className="col-span-2 text-center text-sm text-zinc-500">
            No staff set up yet.
          </p>
        )}
      </div>
    </div>
  );
}

function PinPad({
  staff,
  busy,
  error,
  onSubmit,
  onBack,
}: {
  staff: RosterEntry;
  busy: boolean;
  error: string | null;
  onSubmit: (pin: string) => void;
  onBack: () => void;
}) {
  const [pin, setPin] = useState("");

  function press(digit: string) {
    if (pin.length >= 4) return;
    const next = pin + digit;
    setPin(next);
    if (next.length === 4) {
      onSubmit(next);
      setPin("");
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-zinc-50 p-6 dark:bg-black">
      <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">
        Hi, {staff.name}
      </h1>
      <div className="flex gap-3">
        {[0, 1, 2, 3].map((i) => (
          <span
            key={i}
            className={`h-4 w-4 rounded-full border border-zinc-400 ${
              i < pin.length ? "bg-zinc-900 dark:bg-zinc-50" : ""
            }`}
          />
        ))}
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div className="grid grid-cols-3 gap-3">
        {["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"].map(
          (key, i) =>
            key === "" ? (
              <span key={i} />
            ) : (
              <button
                key={i}
                disabled={busy}
                onClick={() =>
                  key === "⌫" ? setPin(pin.slice(0, -1)) : press(key)
                }
                className="h-16 w-16 rounded-full border border-zinc-200 bg-white text-xl font-medium text-zinc-900 disabled:opacity-50 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50"
              >
                {key}
              </button>
            ),
        )}
      </div>
      <button onClick={onBack} className="text-sm text-zinc-500 underline">
        Back
      </button>
    </div>
  );
}

function ServingScreen({
  staff,
  onSwitchUser,
}: {
  staff: RosterEntry;
  onSwitchUser: () => void;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-zinc-50 p-6 dark:bg-black">
      <p className="text-sm text-zinc-500">Serving</p>
      <h1 className="text-3xl font-semibold text-zinc-900 dark:text-zinc-50">
        {staff.name}
      </h1>
      <p className="max-w-sm text-center text-sm text-zinc-500">
        The sales screen lands in a later slice — this confirms the login and
        idle-lock flow.
      </p>
      <button
        onClick={onSwitchUser}
        className="rounded border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700"
      >
        Switch user
      </button>
    </div>
  );
}
