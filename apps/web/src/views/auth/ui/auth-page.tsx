"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { SignInForm } from "@features/sign-in";
import { SignUpForm } from "@features/sign-up";
import { SocialLoginButtons } from "@features/social-login";
import { AuthCard } from "@widgets/auth-layout";

/**
 * 통합 인증 화면. 로그인/회원가입을 탭으로 전환하며(URL `/login`·`/signup` 구동),
 * 로컬(이메일) + 소셜(Google/Kakao/Naver) 4가지 진입을 한 화면에서 제공한다.
 */
export function AuthPage() {
  const router = useRouter();
  const pathname = usePathname();
  const mode = pathname === "/signup" ? "signup" : "signin";

  function tabClass(active: boolean): string {
    return `rounded-lg py-2 text-sm font-semibold transition-colors ${
      active ? "bg-white text-brand-700 shadow-soft" : "text-neutral-500 hover:text-neutral-800"
    }`;
  }

  return (
    <AuthCard
      title={mode === "signup" ? "회원가입" : "로그인"}
      subtitle={
        mode === "signup"
          ? "레고를 사고팔고 컬렉션을 자랑해보세요."
          : "다시 오신 것을 환영합니다."
      }
    >
      <div className="flex flex-col gap-5">
        <div role="tablist" aria-label="인증 방식" className="grid grid-cols-2 gap-1 rounded-xl bg-neutral-100 p-1">
          <Link
            href="/login"
            role="tab"
            aria-selected={mode === "signin"}
            className={tabClass(mode === "signin")}
          >
            로그인
          </Link>
          <Link
            href="/signup"
            role="tab"
            aria-selected={mode === "signup"}
            className={tabClass(mode === "signup")}
          >
            회원가입
          </Link>
        </div>

        {mode === "signin" ? (
          <SignInForm onSignedIn={() => router.push("/")} />
        ) : (
          <SignUpForm
            onRegistered={(email) => router.push(`/verify?email=${encodeURIComponent(email)}`)}
          />
        )}

        <SocialLoginButtons />
      </div>
    </AuthCard>
  );
}
