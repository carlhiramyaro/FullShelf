import { auth, currentUser } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";
import SignOut from "../_components/SignOut";

// Where a signed-in Clerk user lands when the backend says they own no shop:
// they signed up on their own (sign-up is open), or their owner login was
// replaced by a re-link. The number is a build-time env var so it can differ
// per environment.
export default async function NoShopPage() {
  const { userId } = await auth();
  if (!userId) {
    redirect("/sign-in");
  }
  const user = await currentUser();
  const email = user?.primaryEmailAddress?.emailAddress;
  const phone = process.env.NEXT_PUBLIC_SUPPORT_PHONE;

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-4 p-8">
      <h1 className="text-2xl font-semibold text-ink">No shop linked to this account yet</h1>
      {email && (
        <p className="text-ink">
          You are signed in as <span className="font-medium">{email}</span>.
        </p>
      )}
      <p className="text-ink">
        To get started, contact FullShelf and give them this email address so they can set up your shop.
      </p>
      {phone && (
        <p className="text-lg font-medium text-primary">
          <a href={`tel:${phone.replace(/\s/g, "")}`}>{phone}</a>
        </p>
      )}
      <p className="text-sm text-ink/70">
        Already registered? Wait a minute and refresh this page. If you signed in with the wrong account, sign out and
        try another.
      </p>
      <div>
        <SignOut />
      </div>
    </main>
  );
}
