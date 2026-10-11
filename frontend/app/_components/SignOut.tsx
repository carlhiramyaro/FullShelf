"use client";

import { SignOutButton } from "@clerk/nextjs";

// Ends the owner/admin Clerk session and returns to the login page. Staff
// don't use this: they have "Switch user" on the shop screen.
export default function SignOut() {
  return (
    <SignOutButton redirectUrl="/sign-in">
      <button type="button" className="text-sm font-medium text-secondary hover:text-primary">
        Sign out
      </button>
    </SignOutButton>
  );
}
