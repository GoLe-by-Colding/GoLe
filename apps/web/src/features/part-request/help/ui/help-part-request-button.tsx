"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createDirectRoom } from "@entities/chat";
import { partRequestHref } from "@entities/part-request";
import {
  isThirdPartyProvisionConsentCancelledError,
  ThirdPartyProvisionConsentDialog,
  useSession,
  useThirdPartyProvisionConsent,
} from "@entities/user";
import { ApiError, isOnboardingRequiredError } from "@shared/api";
import { loginHrefWithReturnTo } from "@shared/lib";
import { Button, LinkButton } from "@shared/ui";

export interface HelpPartRequestButtonProps {
  readonly requestId: string;
  readonly requesterId: string;
  /** 대화 입력창 초안에 넣을 요청 제목(예: "3001 라이트 블루이시 그레이 4개"). */
  readonly requestTitle: string;
  readonly setNumber: string | null;
}

/**
 * 1:1 대화는 맥락 없이 열리므로, 어떤 요청을 보고 연락하는지 입력창에 미리 채운다(2026-10-09 로컬 QA).
 * 보내기 전에 고칠 수 있다.
 */
export function helpConversationDraft(requestTitle: string, setNumber: string | null): string {
  const target = setNumber === null ? `「${requestTitle}」` : `「${requestTitle}」(#${setNumber})`;
  return `부품 요청 ${target} 보고 연락드려요. 이 부품 가지고 있어요.`;
}

/**
 * "도와줄게요" — 요청자와 1:1 대화를 열고 그 방으로 보낸다. (wanted-parts W10)
 *
 * 새 기능을 만들지 않고 기존 1:1 대화(`POST /chat/social/rooms/direct`)를 쓴다. 대화 생성은
 * 제3자 제공 동의가 필요할 수 있어, 채팅 화면과 같은 동의 흐름(`SOCIAL_DIRECT_CHAT`)을 거친다.
 */
export function HelpPartRequestButton({
  requestId,
  requesterId,
  requestTitle,
  setNumber,
}: HelpPartRequestButtonProps) {
  const router = useRouter();
  const { session } = useSession();
  const { runWithConsent, dialog } = useThirdPartyProvisionConsent();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (session?.accountId === requesterId) return null;

  if (!session) {
    return (
      <LinkButton href={loginHrefWithReturnTo(partRequestHref(requestId))} size="lg" fullWidth>
        로그인하고 도와주기
      </LinkButton>
    );
  }

  async function openConversation() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const room = await runWithConsent(() => createDirectRoom(requesterId), "SOCIAL_DIRECT_CHAT");
      const query = new URLSearchParams({
        room: room.id,
        draft: helpConversationDraft(requestTitle, setNumber),
      });
      router.push(`/chat?${query.toString()}`);
    } catch (cause) {
      setBusy(false);
      if (isThirdPartyProvisionConsentCancelledError(cause) || isOnboardingRequiredError(cause)) {
        return;
      }
      setError(
        cause instanceof ApiError
          ? cause.message
          : "대화를 열지 못했어요. 잠시 후 다시 시도해 주세요.",
      );
    }
  }

  return (
    <>
      <div className="flex flex-col gap-2">
        <Button size="lg" fullWidth disabled={busy} onClick={() => void openConversation()}>
          {busy ? "대화 여는 중…" : "도와줄게요"}
        </Button>
        {error !== null ? (
          <p role="alert" className="text-sm text-danger">
            {error}
          </p>
        ) : null}
      </div>
      <ThirdPartyProvisionConsentDialog {...dialog} />
    </>
  );
}
