"use client";

import { useEffect, useId, useRef, useState } from "react";
import type { ChatRoom } from "@entities/chat";
import { Button, Heading } from "@shared/ui";

export interface DirectTradeConfirmationProps {
  readonly room: ChatRoom;
  readonly myId: string;
  readonly busy: boolean;
  readonly onToggle: () => void;
}

/**
 * 양측 직거래 확인 상태와 최종 확정 안전장치.
 * 첫 확인과 취소는 즉시 처리하되, 두 번째 확인만 판매 완료를 명시적으로 재확인한다.
 */
export function DirectTradeConfirmation({
  room,
  myId,
  busy,
  onToggle,
}: DirectTradeConfirmationProps) {
  const [finalConfirmationOpen, setFinalConfirmationOpen] = useState(false);
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  const mine = room.buyerId === myId ? room.buyerConfirmedAt : room.sellerConfirmedAt;
  const other = room.buyerId === myId ? room.sellerConfirmedAt : room.buyerConfirmedAt;

  useEffect(() => {
    if (!finalConfirmationOpen) return;
    const restoreTarget = triggerRef.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const handleDialogKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) {
        setFinalConfirmationOpen(false);
        return;
      }
      if (event.key !== "Tab") return;

      const dialog = dialogRef.current;
      if (dialog === null) return;
      const focusable = Array.from(
        dialog.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      );
      if (focusable.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }

      const first = focusable.at(0);
      const last = focusable.at(-1);
      if (first === undefined || last === undefined) return;
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !dialog.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !dialog.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", handleDialogKeyDown);
    return () => {
      document.removeEventListener("keydown", handleDialogKeyDown);
      document.body.style.overflow = previousOverflow;
      window.requestAnimationFrame(() => restoreTarget?.focus());
    };
  }, [busy, finalConfirmationOpen]);

  if (room.directTradeCompletedAt !== null) {
    return (
      <p
        role="status"
        aria-live="polite"
        className="border-b border-success/20 bg-success-soft px-5 py-3 text-sm font-semibold text-success"
      >
        양쪽이 확인해 거래가 완료됐어요.
      </p>
    );
  }

  function requestToggle() {
    if (busy) return;
    if (mine === null) {
      triggerRef.current =
        document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setFinalConfirmationOpen(true);
      return;
    }
    onToggle();
  }

  function finalizeTrade() {
    if (busy) return;
    setFinalConfirmationOpen(false);
    onToggle();
  }

  return (
    <>
      <div className="flex items-center justify-between gap-3 border-b border-brand-100 bg-brand-50/50 px-5 py-3">
        <div>
          <p className="text-sm font-semibold text-neutral-900">직거래 완료 확인</p>
          <p role="status" aria-live="polite" className="text-xs text-neutral-500">
            {other === null ? "상대방 확인을 기다리고 있어요" : "상대방이 거래 완료를 확인했어요"}
          </p>
        </div>
        <Button size="sm" variant="secondary" disabled={busy} onClick={requestToggle}>
          {busy ? "처리 중" : mine === null ? "거래 완료" : "확인 취소"}
        </Button>
      </div>

      {finalConfirmationOpen ? (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-neutral-950/45 px-4"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !busy) setFinalConfirmationOpen(false);
          }}
        >
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            tabIndex={-1}
            className="flex w-full max-w-sm flex-col gap-5 rounded-2xl bg-white p-6 shadow-2xl"
          >
            <div className="flex flex-col gap-2">
              <Heading level={2} id={titleId}>
                {other === null ? "거래 완료를 확인할까요?" : "이 거래를 최종 완료할까요?"}
              </Heading>
              <p id={descriptionId} className="text-sm leading-relaxed text-neutral-600">
                {other === null
                  ? "완료 확인 뒤 상대방도 확인하면 매물이 판매 완료로 바뀌며, 그때부터 확인을 취소할 수 없습니다."
                  : "상대방은 이미 거래 완료를 확인했습니다. 지금 확정하면 매물이 판매 완료로 바뀌며 거래 확인을 취소할 수 없습니다."}
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                fullWidth
                variant="secondary"
                autoFocus
                disabled={busy}
                onClick={() => setFinalConfirmationOpen(false)}
              >
                돌아가기
              </Button>
              <Button fullWidth disabled={busy} onClick={finalizeTrade}>
                {busy ? "처리 중" : other === null ? "거래 완료 확인" : "판매 완료 확정"}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
