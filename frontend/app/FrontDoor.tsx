"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { DEVICE_TOKEN_KEY } from "@/lib/deviceToken";

// Renders nothing. A paired shop device (a localStorage token, invisible to
// the server) is sent on to /shop; every other signed-out browser stays on
// the landing page, which the server has already rendered for crawlers.
// This only checks that a token *exists*; /shop already validates it
// against /api/staff/roster and clears it if revoked, so re-checking here
// would just duplicate that call.
export default function FrontDoor() {
  const router = useRouter();

  useEffect(() => {
    if (localStorage.getItem(DEVICE_TOKEN_KEY)) {
      router.replace("/shop");
    }
  }, [router]);

  return null;
}
