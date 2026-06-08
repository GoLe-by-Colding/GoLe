"use client";

import { useEffect, useState } from "react";
import {
  fetchSocialAuthorizeUrl,
  fetchSocialProviders,
} from "@entities/user";
import { Button } from "@shared/ui";

/** OAuth state 검증용 sessionStorage 키. 콜백 페이지와 공유한다. */
export const OAUTH_STATE_KEY = "gole.oauth.state";

const PROVIDER_LABEL: Record<string, string> = {
  google: "Google로 계속하기",
  kakao: "카카오로 계속하기",
  naver: "네이버로 계속하기",
};

function labelOf(provider: string): string {
  return PROVIDER_LABEL[provider] ?? `${provider}로 계속하기`;
}

/**
 * 활성(설정된) 소셜 provider 버튼을 노출한다. 토큰 미설정 시 목록이 비어 아무것도 렌더하지 않는다.
 * 클릭 시 state를 생성·저장하고 provider 동의 화면으로 리다이렉트한다(CSRF 방지).
 */
export function SocialLoginButtons() {
  const [providers, setProviders] = useState<readonly string[]>([]);
  const [pending, setPending] = useState<string | undefined>(undefined);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    fetchSocialProviders(controller.signal)
      .then(setProviders)
      .catch(() => setProviders([]));
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

  if (providers.length === 0) {
    return null;
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
      {providers.map((provider) => (
        <Button
          key={provider}
          type="button"
          variant="secondary"
          size="lg"
          fullWidth
          disabled={pending !== undefined}
          onClick={() => start(provider)}
        >
          {pending === provider ? "이동 중..." : labelOf(provider)}
        </Button>
      ))}
    </div>
  );
}
