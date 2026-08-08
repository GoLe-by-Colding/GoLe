"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { SignInForm } from "@features/sign-in";
import { SignUpForm } from "@features/sign-up";
import { SocialLoginButtons } from "@features/social-login";
import { AuthCard } from "@widgets/auth-layout";
import { Button } from "@shared/ui";

export interface AuthPageProps {
  /** 소셜 첫 가입 직후 온보딩 환영 화면 표시 여부(서버에서 ?welcome=1 판별). */
  readonly welcome?: boolean;
}

/**
 * 통합 인증 화면. 로그인/회원가입을 탭으로 전환하며(URL `/login`·`/signup` 구동),
 * 로컬(이메일) + 소셜(Google/Kakao/Naver) 4가지 진입을 한 화면에서 제공한다.
 */
export function AuthPage({ welcome = false }: AuthPageProps) {
  const router = useRouter();
  const pathname = usePathname();
  const mode = pathname === "/signup" ? "signup" : "signin";

  if (welcome) {
    return (
      <AuthCard title="환영합니다" subtitle="소셜 계정으로 가입이 완료됐어요.">
        <div className="flex flex-col gap-5">
          <p className="text-sm leading-relaxed text-neutral-600">
            이제 GoLe에서 레고 시세를 확인하고, 안전하게 거래하고, 컬렉션을 자랑할 수 있어요.
          </p>
          <Button size="lg" fullWidth onClick={() => router.replace("/")}>
            시작하기
          </Button>
        </div>
      </AuthCard>
    );
  }

  function tabClass(active: boolean): string {
    return `block border-b-2 py-2.5 text-center text-sm font-semibold transition-colors ${
      active
        ? "border-brand-600 text-brand-700"
        : "border-transparent text-neutral-500 hover:border-neutral-300 hover:text-neutral-800"
    }`;
  }

  return (
    <AuthCard
      title={mode === "signup" ? "회원가입" : "로그인"}
      subtitle={
        mode === "signup" ? "레고를 사고팔고 컬렉션을 자랑해보세요." : "다시 오신 것을 환영합니다."
      }
    >
      <div className="flex flex-col gap-5">
        <div
          role="tablist"
          aria-label="인증 방식"
          className="grid grid-cols-2 border-b border-neutral-200"
        >
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
