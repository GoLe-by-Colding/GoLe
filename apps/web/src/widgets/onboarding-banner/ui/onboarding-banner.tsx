"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchOnboardingStatus, isOnboardingComplete, useSession } from "@entities/user";

const DISMISS_KEY = "gole.onboardingBanner.dismissed";

/**
 * 기존 계정용 프로필 완성 안내(onboarding D6, R12).
 *
 * 스펙 배포 이전에 가입한 계정(`legacyExempt`)은 프로필이 비어 있어도 거래가 막히지 않는다.
 * 그래서 여기서는 **강제 리다이렉트를 하지 않고** 닫을 수 있는 배너만 띄운다 —
 * 이미 쓰고 있던 사용자를 어느 날 갑자기 위저드에 가두지 않기 위해서다.
 *
 * 온보딩이 필요하지만 면제 대상이 아닌 계정은 서버가 거래 시점에 막고(R9), 그때
 * 공용 API 클라이언트가 온보딩으로 안내하므로 여기서 중복해서 알리지 않는다.
 */
export function OnboardingBanner() {
  const { session } = useSession();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (session === null) {
      return;
    }
    if (window.localStorage.getItem(DISMISS_KEY) === "1") {
      return;
    }
    const controller = new AbortController();
    void fetchOnboardingStatus(controller.signal)
      .then((status) => {
        if (!controller.signal.aborted) {
          // `required`가 아니라 단계 플래그로 판정한다 — 면제 계정은 단계가 남아 있어도
          // `required`가 false로 내려올 수 있어서, 그걸 조건에 쓰면 정작 배너를 보여야 할
          // 대상에게 영영 뜨지 않는다.
          setVisible(status.legacyExempt && !isOnboardingComplete(status));
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [session]);

  // 로그아웃 시에는 효과 안에서 상태를 되돌리지 않고 렌더 단계에서 가린다.
  if (session === null || !visible) {
    return null;
  }

  function dismiss(): void {
    window.localStorage.setItem(DISMISS_KEY, "1");
    setVisible(false);
  }

  return (
    <div className="border-b border-brand-200 bg-brand-50 print:hidden">
      <div className="mx-auto flex max-w-5xl items-center gap-3 px-5 py-2.5 text-sm text-brand-900">
        <p className="flex-1">
          프로필을 완성해 보세요. 닉네임과 관심 테마를 등록하면 더 잘 맞는 매물을 추천해 드려요.
        </p>
        <Link
          href="/onboarding"
          className="shrink-0 font-semibold text-brand-700 underline underline-offset-4"
        >
          완성하기
        </Link>
        <button
          type="button"
          onClick={dismiss}
          aria-label="프로필 완성 안내 닫기"
          className="shrink-0 rounded-md px-2 py-1 text-brand-700/70 transition-colors hover:bg-brand-100 hover:text-brand-900"
        >
          ✕
        </button>
      </div>
    </div>
  );
}
