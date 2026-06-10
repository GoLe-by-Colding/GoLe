"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { VerifyEmailForm } from "@features/verify-email";
import { AuthCard } from "@widgets/auth-layout";

function VerifyEmailContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") ?? "";

  return <VerifyEmailForm initialEmail={email} onVerified={() => router.push("/login")} />;
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
