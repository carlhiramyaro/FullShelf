import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { API_BASE_URL } from "@/lib/api";
import FrontDoor from "./FrontDoor";

// "/" isn't a screen of its own — mvp.md has everyone open "the same
// address" (owner, then staff on the shop device) and work out from there
// where they land. A signed-in owner is a server-side fact (Clerk's
// session), so that redirect happens here before anything renders. Whether
// this browser is a paired shop device lives in localStorage instead
// (Phase B), which the server can't see — FrontDoor makes that one
// client-side check and sends the rest of the traffic to /shop or
// /sign-in ("any other browser shows only the owner login").
export default async function Home() {
  const { userId, getToken } = await auth();
  if (userId) {
    // The backend decides who is what: the platform admin has no shop, an
    // owner has one, and anyone else (open sign-up) has neither.
    const headers = { Authorization: `Bearer ${await getToken()}` };
    const asAdmin = await fetch(`${API_BASE_URL}/api/admin/me`, { headers, cache: "no-store" }).catch(() => null);
    if (asAdmin?.ok) {
      redirect("/admin");
    }
    const asOwner = await fetch(`${API_BASE_URL}/api/owner/me`, { headers, cache: "no-store" }).catch(() => null);
    redirect(asOwner && (asOwner.status === 401 || asOwner.status === 403) ? "/no-shop" : "/owner");
  }

  return <FrontDoor />;
}
