"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { VerifyEmailForm } from "@features/verify-email";
import { AuthCard } from "@widgets/auth-layout";
import { resolveReturnTo } from "@shared/lib";

function VerifyEmailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") ?? "";
  const returnTo = resolveReturnTo(searchParams.get("returnTo"));
  const loginHref =
    returnTo === null ? "/login" : `/login?returnTo=${encodeURIComponent(returnTo)}`;

  return <VerifyEmailForm initialEmail={email} onVerified={() => router.replace(loginHref)} />;
}

export function VerifyEmailPage() {
  return (
    <AuthCard title="이메일 인증" subtitle="받은 인증 코드를 입력해 가입을 완료하세요.">
      <Suspense fallback={null}>
        <VerifyEmailContent />
      </Suspense>
    </AuthCard>
  );
}
