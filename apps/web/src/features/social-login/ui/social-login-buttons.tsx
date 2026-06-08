"use client";

import { useEffect, useState } from "react";
import {
  fetchSocialAuthorizeUrl,
  fetchSocialProviders,
} from "@entities/user";

/** OAuth state 검증용 sessionStorage 키. 콜백 페이지와 공유한다. */
export const OAUTH_STATE_KEY = "gole.oauth.state";

interface ProviderMeta {
  readonly key: string;
  readonly label: string;
  readonly className: string;
}

/** 노출 순서·라벨·브랜드 스타일. 항상 3개를 보여준다(미설정은 비활성). */
const PROVIDERS: readonly ProviderMeta[] = [
  {
    key: "google",
    label: "Google로 계속하기",
    className: "bg-white text-neutral-800 border border-neutral-300 hover:bg-neutral-50",
  },
  {
    key: "kakao",
    label: "카카오로 계속하기",
    className: "bg-[#FEE500] text-[#191600] hover:brightness-95",
  },
  {
    key: "naver",
    label: "네이버로 계속하기",
    className: "bg-[#03C75A] text-white hover:brightness-95",
  },
];

/**
 * 소셜 로그인 버튼. Google/Kakao/Naver 3개를 항상 노출한다.
 * 백엔드에 토큰(client-id)이 설정된 provider만 활성화되고, 미설정 provider는 "준비 중"으로 비활성.
 * 클릭 시 state를 생성·저장하고 동의 화면으로 이동한다(CSRF 방지).
 */
export function SocialLoginButtons() {
  const [enabled, setEnabled] = useState<readonly string[]>([]);
  const [pending, setPending] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    fetchSocialProviders(controller.signal)
      .then(setEnabled)
      .catch(() => setEnabled([]));
    return () => controller.abort();
  }, []);

  async function start(provider: string) {
    setError(undefined);
    setPending(provider);
    try {
      const state = crypto.randomUUID();
      window.sessionStorage.setItem(OAUTH_STATE_KEY, state);
      const redirectUri = `${window.location.origin}/auth/callback/${provider}`;
      const { url } = await fetchSocialAuthorizeUrl(provider, redirectUri, state);
      window.location.assign(url);
    } catch {
      setError("소셜 로그인을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      setPending(undefined);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3 text-xs text-neutral-400">
        <span className="h-px flex-1 bg-neutral-200" />
        또는
        <span className="h-px flex-1 bg-neutral-200" />
      </div>
      {error ? (
        <p className="rounded-md bg-danger-soft p-2 text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
      {PROVIDERS.map((provider) => {
        const isEnabled = enabled.includes(provider.key);
        return (
          <button
            key={provider.key}
            type="button"
            disabled={!isEnabled || pending !== undefined}
            onClick={() => start(provider.key)}
            aria-label={isEnabled ? provider.label : `${provider.label} (준비 중)`}
            className={`inline-flex h-12 w-full items-center justify-center gap-2 rounded-lg text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${provider.className}`}
          >
            {pending === provider.key
              ? "이동 중..."
              : isEnabled
                ? provider.label
                : `${provider.label} (준비 중)`}
          </button>
        );
      })}
    </div>
  );
}
