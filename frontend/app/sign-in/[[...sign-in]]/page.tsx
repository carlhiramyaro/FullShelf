import Link from "next/link";
import { SignIn } from "@clerk/nextjs";

export default function SignInPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-bg">
      <SignIn />
      <p className="text-sm text-ink/70">
        Setting up the shop&apos;s device?{" "}
        <Link href="/shop" className="font-medium text-secondary hover:underline">
          Enter pairing code
        </Link>
      </p>
    </div>
  );
}
