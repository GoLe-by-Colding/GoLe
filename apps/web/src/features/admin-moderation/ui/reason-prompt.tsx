"use client";

import { useEffect, useState } from "react";
import { Button, Heading, Text, Textarea } from "@shared/ui";

export interface ReasonPromptProps {
  /** 모달 제목. 예: "매물 내리기" */
  readonly title: string;
  /** 조치 대상 설명. 예: "브릭 세트 75192 밀레니엄 팔콘" */
  readonly target: string;
  /** 확인 버튼 라벨. 기본 "조치하기" */
  readonly confirmLabel?: string | undefined;
  readonly busy?: boolean | undefined;
  readonly error?: string | undefined;
  readonly onConfirm: (reason: string) => void;
  readonly onCancel: () => void;
}

/**
 * 사유 입력 확인 모달.
 *
 * 매물 내림·게시글 삭제·계정 정지는 모두 사유가 필수다(요구사항 4.2, 5.2, 6.2).
 * 사유는 감사 로그에 그대로 남아 분쟁 대응 근거가 되므로, 브라우저 confirm 대신
 * 실제 입력을 받는 공용 컴포넌트로 통일한다.
 */
export function ReasonPrompt({
  title,
  target,
  confirmLabel = "조치하기",
  busy = false,
  error,
  onConfirm,
  onCancel,
}: ReasonPromptProps) {
  const [reason, setReason] = useState("");

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onCancel();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onCancel]);

  const trimmed = reason.trim();

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-neutral-950/60 p-4"
    >
      <div className="flex w-full max-w-md flex-col gap-4 rounded-lg bg-white p-6 shadow-xl">
        <div className="flex flex-col gap-1">
          <Heading level={3}>{title}</Heading>
          <Text tone="muted" size="sm">
            {target}
          </Text>
        </div>

        <label className="flex flex-col gap-1.5 text-sm font-medium text-neutral-700">
          조치 사유
          <Textarea
            autoFocus
            rows={3}
            value={reason}
            placeholder="예: 가품으로 확인됨 (신고 #123)"
            onChange={(e) => setReason(e.target.value)}
          />
          <span className="text-xs font-normal text-neutral-500">
            사유는 감사 로그에 기록되어 되돌릴 수 없습니다.
          </span>
        </label>

        {error !== undefined ? <p className="text-sm text-danger">{error}</p> : null}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={() => onConfirm(trimmed)}
            disabled={busy || trimmed.length === 0}
          >
            {busy ? "처리 중..." : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
