import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import { API_BASE_URL } from "@/lib/api";
import type { Metadata } from "next";
import FrontDoor from "./FrontDoor";
import Landing from "./_components/Landing";

export const metadata: Metadata = {
  title: "FullShelf: stock, sales and cash for small shops",
  alternates: { canonical: "/" },
  openGraph: {
    title: "FullShelf: stock, sales and cash for small shops",
    description:
      "Stock, sales and expected cash for grocery shops and mini-marts in Ghana. Staff key each sale; the owner sees stock, sales and the cash to expect from her phone.",
    url: "/",
    siteName: "FullShelf",
    type: "website",
  },
};

// "/" is the front door: everyone opens "the same address" (mvp.md) and
// works out from there where they land. A signed-in user is a server-side
// fact (Clerk's session), so that redirect happens here before anything
// renders. A signed-out visitor gets the public landing page, rendered on
// the server so a crawler can read it. Whether this browser is a paired shop
// device lives in localStorage (Phase B), which the server can't see:
// FrontDoor makes that one client-side check and sends it on to /shop.
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

  return (
    <>
      <FrontDoor />
      <Landing />
    </>
  );
}
