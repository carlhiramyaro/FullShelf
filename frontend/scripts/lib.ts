// Shared by seed-day.ts and screenshots.ts. Local development only: every
// entry point calls guardLocal() first, because production holds Aunt
// Amerley's real ledger and these scripts write to it through the real
// endpoints (sales and voids can only be reversed, never deleted).
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

function loadEnvFile(): Record<string, string> {
  const out: Record<string, string> = {};
  try {
    for (const line of readFileSync(resolve(__dirname, "../.env.local"), "utf8").split("\n")) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
      if (m) out[m[1]] = m[2].replace(/^["']|["']$/g, "");
    }
  } catch {
    // no .env.local: rely on the real environment
  }
  return out;
}

const fileEnv = loadEnvFile();
const env = (k: string) => process.env[k] ?? fileEnv[k];

export const OWNER_EMAIL = env("SEED_OWNER_EMAIL") ?? "carlhiramyaro@outlook.com";
export const API = (env("API_BASE_URL") ?? env("NEXT_PUBLIC_API_BASE_URL") ?? "http://localhost:8080").replace(/\/$/, "");
export const APP = (env("APP_BASE_URL") ?? "http://localhost:3001").replace(/\/$/, "");
const CLERK_SECRET = env("CLERK_SECRET_KEY") ?? "";
const CLERK_API = "https://api.clerk.com/v1";

// Two counter staff. Created by the seed (or, if they exist, given this PIN).
export const STAFF = [
  { name: "Abena Owusu", pin: "2468" },
  { name: "Kofi Mensah", pin: "1357" },
];

export function guardLocal(): void {
  const local = (u: string) => /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(u);
  if (!local(API) || !local(APP)) {
    throw new Error(`Refusing to run: API (${API}) and app (${APP}) must both be localhost.`);
  }
  if (!CLERK_SECRET.startsWith("sk_test_")) {
    throw new Error("Refusing to run: CLERK_SECRET_KEY must be a Clerk development key (sk_test_...).");
  }
}

export function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

async function readJson(res: Response): Promise<unknown> {
  const text = await res.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return text;
  }
}

function errorText(body: unknown, status: number): string {
  if (body && typeof body === "object") {
    const b = body as { message?: string; errors?: { long_message?: string; message?: string }[] };
    if (b.message) return b.message;
    if (b.errors?.[0]) return b.errors[0].long_message ?? b.errors[0].message ?? `HTTP ${status}`;
  }
  return typeof body === "string" && body ? body : `HTTP ${status}`;
}

export async function clerk<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const res = await fetch(`${CLERK_API}${path}`, {
    method: init.method ?? "GET",
    headers: { Authorization: `Bearer ${CLERK_SECRET}`, "Content-Type": "application/json" },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  const body = await readJson(res);
  if (!res.ok) throw new Error(`Clerk ${init.method ?? "GET"} ${path}: ${errorText(body, res.status)}`);
  return body as T;
}

let clerkUserId: string | undefined;
export async function ownerClerkUserId(): Promise<string> {
  if (clerkUserId) return clerkUserId;
  const users = await clerk<{ id: string }[]>(`/users?email_address=${encodeURIComponent(OWNER_EMAIL)}`);
  if (!users.length) throw new Error(`No Clerk user with email ${OWNER_EMAIL} in this Clerk instance.`);
  clerkUserId = users[0].id;
  return clerkUserId;
}

// The owner endpoints want a Clerk session JWT. Clerk's Backend API can mint
// one for a user on a development instance: open a session, then ask for its
// token. Tokens last about a minute, so one is minted fresh when the old one
// is nearly spent.
let session: { id: string; jwt?: string; at: number } | undefined;
export async function ownerJwt(): Promise<string> {
  if (session?.jwt && Date.now() - session.at < 30_000) return session.jwt;
  if (!session) {
    const s = await clerk<{ id: string }>("/sessions", { method: "POST", body: { user_id: await ownerClerkUserId() } });
    session = { id: s.id, at: 0 };
  }
  const t = await clerk<{ jwt: string }>(`/sessions/${session.id}/tokens`, { method: "POST", body: {} });
  session.jwt = t.jwt;
  session.at = Date.now();
  return t.jwt;
}

// A one-time ticket Playwright can use to sign in as the owner without
// typing a password or a verification code.
export async function ownerSignInTicket(): Promise<string> {
  const t = await clerk<{ token: string }>("/sign_in_tokens", {
    method: "POST",
    body: { user_id: await ownerClerkUserId(), expires_in_seconds: 600 },
  });
  return t.token;
}

type Auth = { owner?: boolean; device?: string; staff?: string };

export async function api<T = unknown>(
  path: string,
  opts: { method?: string; body?: unknown; auth?: Auth } = {},
): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (opts.auth?.owner) headers.Authorization = `Bearer ${await ownerJwt()}`;
  if (opts.auth?.device) headers["X-Device-Token"] = opts.auth.device;
  if (opts.auth?.staff) headers["X-Staff-Session-Token"] = opts.auth.staff;
  const method = opts.method ?? (opts.body === undefined ? "GET" : "POST");
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  const body = await readJson(res);
  if (!res.ok) throw new Error(`${method} ${path}: ${errorText(body, res.status)}`);
  return body as T;
}

export const owner = <T = unknown>(path: string, body?: unknown, method?: string) =>
  api<T>(path, { body, method, auth: { owner: true } });

// Pairs a fresh device through the real flow (owner makes a code, the device
// redeems it) and returns the token plus a function that revokes it again.
export async function pairDevice(): Promise<{ deviceToken: string; revoke: () => Promise<void> }> {
  const before = new Set((await owner<{ id: number }[]>("/api/owner/devices")).map((d) => d.id));
  const { code } = await owner<{ code: string }>("/api/owner/devices/pairing-code", {});
  const { deviceToken } = await api<{ deviceToken: string }>("/api/devices/pair", { body: { code } });
  const created = (await owner<{ id: number; revoked: boolean }[]>("/api/owner/devices")).filter((d) => !before.has(d.id));
  return {
    deviceToken,
    revoke: async () => {
      for (const d of created) await owner(`/api/owner/devices/${d.id}/revoke`, {});
    },
  };
}

export async function staffLogin(deviceToken: string, name: string, pin: string): Promise<string> {
  const roster = await api<{ id: number; name: string }[]>("/api/staff/roster", { auth: { device: deviceToken } });
  const who = roster.find((r) => r.name === name);
  if (!who) throw new Error(`${name} is not on the roster.`);
  const { sessionToken } = await api<{ sessionToken: string }>("/api/staff/login", {
    body: { staffId: who.id, pin },
    auth: { device: deviceToken },
  });
  return sessionToken;
}
