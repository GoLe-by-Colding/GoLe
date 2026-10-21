"use client";

import { type ReactNode, Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { fetchLaunchConfig } from "@entities/launch";
import {
  fetchOnboardingStatus,
  loadSession,
  saveSession,
  isOnboardingComplete,
  nextIncompleteStep,
  onboardingSteps,
  type OnboardingStatus,
  type OnboardingStep,
  withStepCompleted,
} from "@entities/user";
import { OnboardingConsentForm } from "@features/onboarding/agree-onboarding-terms";
import { InterestTagsPicker } from "@features/onboarding/select-interest-tags";
import { SetNicknameForm } from "@features/onboarding/set-nickname";
import { VerifyPhoneForm } from "@features/onboarding/verify-phone";
import { AuthCard } from "@widgets/auth-layout";
import { ApiError } from "@shared/api";
import { loginHrefWithReturnTo, resolveReturnTo } from "@shared/lib";
import { LinkButton, Text } from "@shared/ui";
import { stepTitle } from "../model/steps";

/**
 * 최초 로그인 온보딩 위저드(R11).
 *
 * 닉네임 → (정책상 필수일 때만 전화인증) → 관심태그 → 동의 순으로 진행하되, 진입할 때
 * 서버 상태를 받아 **필요 없거나 이미 끝난 단계는 건너뛴다**. 각 단계는 성공 즉시 서버에
 * 저장되므로(D1) 중간에 이탈해도 다음에 같은 지점에서 이어진다.
 */
export function OnboardingPage() {
  return (
    <Suspense fallback={<OnboardingShell>{null}</OnboardingShell>}>
      <OnboardingContent />
    </Suspense>
  );
}

function OnboardingShell({
  title = "프로필 완성",
  subtitle,
  children,
}: {
  readonly title?: string;
  readonly subtitle?: string;
  readonly children: ReactNode;
}) {
  return (
    <AuthCard title={title} {...(subtitle === undefined ? {} : { subtitle })}>
      {children}
    </AuthCard>
  );
}

function OnboardingContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<OnboardingStatus | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [sellerReady, setSellerReady] = useState(false);
  const forSeller = searchParams.get("purpose") === "seller";
  const requirePhone = forSeller && sellerReady;

  // 사용자가 넘겨준 값이므로 형태 검증을 통과한 경로만 쓴다(오픈 리다이렉트 방어).
  const returnTo = resolveReturnTo(searchParams.get("returnTo"));

  const finish = useCallback(() => {
    const session = loadSession();
    if (session !== null && session.onboardingRequired)
      saveSession({ ...session, onboardingRequired: false });
    router.replace(returnTo ?? "/");
  }, [router, returnTo]);

  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      fetchOnboardingStatus(controller.signal),
      forSeller ? fetchLaunchConfig(controller.signal) : Promise.resolve(null),
    ])
      .then(([nextStatus, launch]) => {
        if (controller.signal.aborted) return;
        setSellerReady(launch?.sellerIdentityVerificationReady === true);
        setStatus(nextStatus);
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(cause instanceof ApiError ? cause.message : "온보딩 정보를 불러오지 못했습니다.");
      });
    return () => controller.abort();
  }, [forSeller]);

  // 이미 온보딩을 마친 계정이 주소로 직접 들어온 경우 되돌려보낸다.
  //
  // 판정에 `required`를 쓰지 않는다 — 면제(legacyExempt) 계정은 단계가 남아 있어도
  // `required`가 false로 내려올 수 있어서, 배너를 보고 자발적으로 완성하러 들어온
  // 사용자를 입구에서 되돌려보내게 된다.
  useEffect(() => {
    if (
      status !== null &&
      (!forSeller || sellerReady) &&
      isOnboardingComplete(status, requirePhone)
    ) {
      finish();
    }
  }, [status, finish, forSeller, sellerReady, requirePhone]);

  if (error !== undefined) {
    return (
      <OnboardingShell subtitle="잠시 후 다시 시도해 주세요.">
        <div className="flex flex-col gap-4">
          <Text tone="secondary">{error}</Text>
          <LinkButton href={loginHrefWithReturnTo(returnTo ?? (forSeller ? "/sell" : "/profile"))}>
            로그인 화면으로
          </LinkButton>
        </div>
      </OnboardingShell>
    );
  }

  if (status === null) {
    return (
      <OnboardingShell>
        <p role="status" aria-live="polite" className="py-6 text-center text-sm text-neutral-500">
          불러오는 중…
        </p>
      </OnboardingShell>
    );
  }

  if (forSeller && !sellerReady) {
    return (
      <OnboardingShell
        title="판매자 인증 준비 중"
        subtitle="판매자 인증을 준비하고 있어요. 잠시 후 다시 시도해 주세요."
      >
        <LinkButton href={returnTo ?? "/sell"}>상품 등록으로 돌아가기</LinkButton>
      </OnboardingShell>
    );
  }

  const current = nextIncompleteStep(status, requirePhone);
  if (current === null) {
    // 마지막 단계까지 끝났다. finish()가 곧 이동시킨다.
    return (
      <OnboardingShell subtitle="프로필 확인을 마쳤어요. 하던 작업으로 돌아갑니다.">
        <p role="status" aria-live="polite" className="py-6 text-center text-sm text-neutral-500">
          이동 중…
        </p>
      </OnboardingShell>
    );
  }

  function completeStep(step: OnboardingStep): void {
    setStatus((prev) => (prev === null ? prev : withStepCompleted(prev, step)));
  }

  const steps = onboardingSteps(status, requirePhone);
  const stepIndex = steps.indexOf(current);

  return (
    <OnboardingShell title={stepTitle(current)} subtitle={`${stepIndex + 1}/${steps.length} 단계`}>
      <div className="flex flex-col gap-5">
        <StepIndicator current={stepIndex} steps={steps} />
        {current === "nickname" ? (
          <SetNicknameForm onCompleted={() => completeStep("nickname")} />
        ) : null}
        {current === "phone" ? <VerifyPhoneForm onCompleted={() => completeStep("phone")} /> : null}
        {current === "interestTags" ? (
          <InterestTagsPicker
            initialSelected={status.interestTags}
            onCompleted={() => completeStep("interestTags")}
          />
        ) : null}
        {current === "consent" ? (
          <OnboardingConsentForm onCompleted={() => completeStep("consent")} />
        ) : null}
      </div>
    </OnboardingShell>
  );
}

/** 남은 단계를 눈으로 가늠하게 해준다. 진행 상태는 서버 응답에서만 파생된다. */
function StepIndicator({
  current,
  steps,
}: {
  readonly current: number;
  readonly steps: readonly OnboardingStep[];
}) {
  return (
    <ol className="flex gap-1.5" aria-label={`전체 ${steps.length}단계 중 ${current + 1}단계`}>
      {steps.map((step, index) => (
        <li
          key={step}
          aria-hidden="true"
          className={`h-1.5 flex-1 rounded-full ${
            index <= current ? "bg-brand-600" : "bg-neutral-200"
          }`}
        />
      ))}
    </ol>
  );
}
