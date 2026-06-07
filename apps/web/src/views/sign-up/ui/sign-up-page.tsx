"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { SignUpForm } from "@features/sign-up";
import { AuthCard } from "@widgets/auth-layout";

export function SignUpPage() {
  const router = useRouter();

  return (
    <AuthCard
      title="회원가입"
      subtitle="레고를 사고팔고 컬렉션을 자랑해보세요."
      footer={
        <>
          이미 계정이 있으신가요? <Link href="/login">로그인</Link>
        </>
      }
    >
      <SignUpForm
        onRegistered={(email) =>
          router.push(`/verify?email=${encodeURIComponent(email)}`)
        }
      />
    </AuthCard>
  );
}
