"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { DEVICE_TOKEN_KEY } from "@/lib/deviceToken";

// Reached only when the server-side check in page.tsx found no signed-in
// owner. The one thing left to tell apart is a paired shop device (a
// localStorage token, invisible to the server) from a browser that's
// neither — mvp.md's "any other browser shows only the owner login".
// This only checks that a token *exists*; /shop already validates it
// against /api/staff/roster and clears it if revoked, so re-checking here
// would just duplicate that call.
export default function FrontDoor() {
  const router = useRouter();

  useEffect(() => {
    const hasDeviceToken = Boolean(localStorage.getItem(DEVICE_TOKEN_KEY));
    router.replace(hasDeviceToken ? "/shop" : "/sign-in");
  }, [router]);

  return <div className="min-h-screen bg-bg" />;
}
