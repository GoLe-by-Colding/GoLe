"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { saveSession, socialCallback } from "@entities/user";
import { OAUTH_STATE_KEY } from "@features/social-login";
import { ApiError } from "@shared/api";
import { Container, Heading, LinkButton, Text } from "@shared/ui";

export interface OAuthCallbackPageProps {
  readonly provider: string;
}

/**
 * OAuth 콜백 처리. provider에서 돌아온 code/state를 검증하고 세션을 발급받아 저장한 뒤 홈으로 이동한다.
 * (소셜 로그인 스펙 S9)
 */
export function OAuthCallbackPage({ provider }: OAuthCallbackPageProps) {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState<string | undefined>(undefined);
  const ranRef = useRef(false);

  useEffect(() => {
    if (ranRef.current) {
      return; // StrictMode 이중 실행 방지(코드는 1회용)
    }
    ranRef.current = true;

    const run = async (): Promise<void> => {
      const code = params.get("code");
      const returnedState = params.get("state");
      const providerError = params.get("error");

      if (providerError !== null) {
        setError("소셜 로그인이 취소되었거나 거부되었습니다.");
        return;
      }
      if (code === null) {
        setError("인증 코드가 없습니다.");
        return;
      }

      const savedState = window.sessionStorage.getItem(OAUTH_STATE_KEY);
      window.sessionStorage.removeItem(OAUTH_STATE_KEY);
      if (savedState !== null && returnedState !== null && savedState !== returnedState) {
        setError("보안 검증(state)에 실패했습니다. 다시 시도해 주세요.");
        return;
      }

      const redirectUri = `${window.location.origin}/auth/callback/${provider}`;
      try {
        const session = await socialCallback(provider, code, redirectUri);
        saveSession(session);
        router.replace("/");
      } catch (cause) {
        setError(
          cause instanceof ApiError
            ? cause.message
            : "소셜 로그인 처리 중 오류가 발생했습니다.",
        );
      }
    };

    void run();
  }, [params, provider, router]);

  return (
    <Container width="sm">
      <div className="flex flex-col items-start gap-4 pt-16 pb-20">
        {error === undefined ? (
          <>
            <Heading level={1}>로그인 처리 중...</Heading>
            <Text tone="secondary">잠시만 기다려 주세요.</Text>
          </>
        ) : (
          <>
            <Heading level={1}>로그인 실패</Heading>
            <Text tone="secondary">{error}</Text>
            <LinkButton href="/login">로그인 화면으로</LinkButton>
          </>
        )}
      </div>
    </Container>
  );
}
