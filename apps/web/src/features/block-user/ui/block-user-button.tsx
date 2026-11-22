"use client";

import { useState } from "react";
import Link from "next/link";
import { blockChatUser } from "@entities/chat";
import { useSession } from "@entities/user";
import { Button, useDialog } from "@shared/ui";

export interface BlockUserButtonProps {
  readonly targetId: string;
  readonly compact?: boolean;
  /** 차단이 서버에 반영된 직후 한 번 호출된다. */
  readonly onBlocked?: () => void;
}

/**
 * 사용자 차단 — 확인 모달을 거쳐 차단하고, 이 자리를 "차단함 · 차단 관리"로 바꾼다.
 * 비로그인이거나 본인이면 그리지 않는다. 해제는 프로필의 차단 관리에서 한다.
 */
export function BlockUserButton({ targetId, compact = false, onBlocked }: BlockUserButtonProps) {
  const { session } = useSession();
  const dialog = useDialog();
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [failed, setFailed] = useState(false);

  if (session === null || session.accountId === targetId) {
    return null;
  }

  async function handleBlock() {
    if (busy) return;
    const confirmed = await dialog.confirm({
      title: "이 사용자를 차단할까요?",
      description:
        "서로의 글과 댓글이 보이지 않고, 댓글·좋아요·팔로우·대화를 할 수 없어요. 운영자 확인이 필요하면 신고해 주세요. 해제는 프로필의 차단 관리에서 할 수 있어요.",
      confirmLabel: "차단",
      tone: "danger",
    });
    if (!confirmed) return;
    setBusy(true);
    setFailed(false);
    try {
      await blockChatUser(targetId);
      setBlocked(true);
      onBlocked?.();
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {blocked ? (
        <span className="inline-flex items-center gap-2 text-xs text-neutral-500">
          <span>차단함</span>
          <Link href="/profile/blocks" className="font-semibold text-brand-700 hover:underline">
            차단 관리
          </Link>
        </span>
      ) : (
        <span className="inline-flex items-center gap-2">
          <Button
            variant="secondary"
            size={compact ? "sm" : "md"}
            disabled={busy}
            onClick={() => void handleBlock()}
          >
            차단
          </Button>
          {failed ? (
            <span className="text-xs text-danger" role="alert">
              차단하지 못했어요. 잠시 후 다시 시도해 주세요.
            </span>
          ) : null}
        </span>
      )}
      {dialog.element}
    </>
  );
}
