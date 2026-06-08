"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { SignInForm } from "@features/sign-in";
import { SocialLoginButtons } from "@features/social-login";
import { AuthCard } from "@widgets/auth-layout";

export function SignInPage() {
  const router = useRouter();

  return (
    <AuthCard
      title="로그인"
      subtitle="다시 오신 것을 환영합니다."
      footer={
        <>
          계정이 없으신가요? <Link href="/signup">회원가입</Link>
        </>
      }
    >
      <div className="flex flex-col gap-5">
        <SignInForm onSignedIn={() => router.push("/")} />
        <SocialLoginButtons />
      </div>
    </AuthCard>
  );
}
