import { auth } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
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
  const { userId } = await auth();
  if (userId) {
    redirect("/owner");
  }

  return <FrontDoor />;
}
