import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";

// /owner/** is Aunt Amerley's side of the app (staff and device management,
// and later the dashboard/day-close screens) — gated here for UX, on top of
// the backend's own Clerk-JWT enforcement on /api/owner/** which is the real
// security boundary. /shop is deliberately left out: it authenticates via
// device/PIN tokens, a separate scheme from Clerk (see CLAUDE.md).
const isOwnerRoute = createRouteMatcher(["/owner(.*)"]);

export default clerkMiddleware(async (auth, req) => {
  if (isOwnerRoute(req)) {
    await auth.protect();
  }
});

export const config = {
  matcher: [
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    "/(api|trpc)(.*)",
  ],
};
