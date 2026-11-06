"use client";

import { SignOutButton } from "@clerk/nextjs";

// Ends the owner/admin Clerk session. Defaults to the landing page; screens
// where signing out means "switch account" pass "/sign-in". Staff don't use
// this: they have "Switch user" on the shop screen.
export default function SignOut({
  redirectUrl = "/",
  className = "text-sm font-medium text-secondary hover:text-primary",
}: {
  redirectUrl?: string;
  className?: string;
}) {
  return (
    <SignOutButton redirectUrl={redirectUrl}>
      <button type="button" className={className}>
        Sign out
      </button>
    </SignOutButton>
  );
}
