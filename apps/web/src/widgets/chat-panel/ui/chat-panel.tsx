"use client";

import { type FormEvent, useEffect, useId, useRef, useState } from "react";
import {
  reportChatMessage,
  useConversation,
  useRoomReadReceipt,
  type ChatReportReason,
} from "@entities/chat";
import { Button, Skeleton } from "@shared/ui";
import { cn } from "@shared/lib";

export interface ChatPanelProps {
  readonly roomId: string;
  readonly myId: string;
  readonly readOnlyReason?: string;
  readonly hiddenSenderIds?: readonly string[];
  readonly showSenderIdentity?: boolean;
  readonly onManageSender?: (senderId: string) => void;
  readonly onRoomRead?: (roomId: string) => void;
}

/**
 * 실시간 채팅 패널. SSE로 새 메시지를 수신하고 REST로 전송한다.
 */
export function ChatPanel({
  roomId,
  myId,
  readOnlyReason,
  hiddenSenderIds = [],
  showSenderIdentity = false,
  onManageSender,
  onRoomRead,
}: ChatPanelProps) {
  const { messages, send, loadOlder, retry, hasOlder, loadingOlder, olderError, loading, error } =
    useConversation(roomId);
  useRoomReadReceipt({
    roomId,
    myId,
    messages,
    ...(onRoomRead === undefined ? {} : { onRead: onRoomRead }),
  });

  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | undefined>();
  const [reportingId, setReportingId] = useState<string | null>(null);
  const [reportReason, setReportReason] = useState<ChatReportReason>("INAPPROPRIATE");
  const [reportDetail, setReportDetail] = useState("");
  const [reportBusy, setReportBusy] = useState(false);
  const [reportNotice, setReportNotice] = useState<string | undefined>();
  const messageInputId = useId();
  const bottomRef = useRef<HTMLDivElement>(null);
  const previousLastMessageIdRef = useRef<string | null>(null);
  const lastMessageId = messages.at(-1)?.id ?? null;

  // 새 메시지가 뒤에 붙을 때만 말단으로 이동한다. 이전 이력을 앞에 붙이는 동작은
  // 마지막 ID가 그대로라 현재 읽던 위치를 빼앗지 않는다.
  useEffect(() => {
    const previous = previousLastMessageIdRef.current;
    previousLastMessageIdRef.current = lastMessageId;
    if (lastMessageId !== null && lastMessageId !== previous) {
      bottomRef.current?.scrollIntoView({ behavior: previous === null ? "auto" : "smooth" });
    }
  }, [lastMessageId]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!input.trim() || sending) return;
    setSending(true);
    setSendError(undefined);
    try {
      await send(input.trim());
      setInput("");
    } catch {
      setSendError("메시지를 보내지 못했습니다. 대화 권한과 네트워크를 확인해 주세요.");
    } finally {
      setSending(false);
    }
  }

  async function submitReport() {
    if (reportingId === null || reportBusy) return;
    setReportBusy(true);
    setReportNotice(undefined);
    try {
      await reportChatMessage(reportingId, reportReason, reportDetail.trim() || undefined);
      setReportingId(null);
      setReportDetail("");
      setReportNotice("신고가 접수됐습니다. 운영팀은 신고 시점의 대화 문맥만 확인합니다.");
    } catch {
      setReportNotice("신고를 접수하지 못했습니다. 이미 접수한 메시지인지 확인해 주세요.");
    } finally {
      setReportBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-2 p-4">
        <Skeleton className="h-8 w-3/4 rounded-lg" />
        <Skeleton className="h-8 w-1/2 rounded-lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-40 flex-col items-center justify-center gap-3 p-4 text-center">
        <p className="text-sm text-danger">{error}</p>
        <Button type="button" size="sm" variant="secondary" onClick={retry}>
          다시 시도
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* 메시지 영역 */}
      <div
        role="log"
        aria-label="대화 메시지"
        aria-live="polite"
        aria-relevant="additions text"
        className="flex flex-1 flex-col gap-2 overflow-y-auto p-4"
      >
        {hasOlder && messages.length > 0 ? (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="mx-auto"
            disabled={loadingOlder}
            onClick={() => void loadOlder()}
          >
            {loadingOlder ? "불러오는 중" : "이전 메시지"}
          </Button>
        ) : null}
        {olderError ? <p className="text-center text-xs text-danger">{olderError}</p> : null}
        {messages.length === 0 ? (
          <p className="text-center text-sm text-neutral-400">
            첫 메시지를 보내 대화를 시작해보세요!
          </p>
        ) : null}
        {reportNotice ? (
          <p className="mx-auto max-w-md rounded-lg bg-neutral-50 px-3 py-2 text-center text-xs text-neutral-600">
            {reportNotice}
          </p>
        ) : null}
        {messages.map((m) => {
          const mine = m.senderId === myId;
          const hidden = !mine && hiddenSenderIds.includes(m.senderId);
          if (hidden) {
            return (
              <div key={m.id} className="group flex justify-start">
                <div className="flex flex-wrap items-center gap-2 rounded-xl border border-neutral-200 bg-neutral-50 px-3 py-2">
                  <p className="text-xs text-neutral-500">
                    {showSenderIdentity ? `${senderLabel(m.senderId)}의 ` : "차단한 사용자의 "}
                    메시지를 숨겼습니다.
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setReportingId(m.id);
                      setReportNotice(undefined);
                    }}
                    className="min-h-9 min-w-11 rounded-md px-2 text-xs font-medium text-neutral-400 transition-colors hover:bg-white hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400"
                  >
                    신고
                  </button>
                  {onManageSender ? (
                    <button
                      type="button"
                      onClick={() => onManageSender(m.senderId)}
                      className="min-h-9 rounded-md px-2 text-xs font-medium text-brand-700 transition-colors hover:bg-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400"
                    >
                      차단 해제
                    </button>
                  ) : null}
                </div>
              </div>
            );
          }
          return (
            <div key={m.id} className={cn("group flex", mine ? "justify-end" : "justify-start")}>
              <div
                className={cn(
                  "flex max-w-[78%] flex-col gap-1",
                  mine ? "items-end" : "items-start",
                )}
              >
                {showSenderIdentity && !mine ? (
                  <div className="flex min-h-7 items-center gap-1.5 px-1">
                    <span
                      className="max-w-48 truncate font-mono text-[11px] font-semibold text-neutral-500"
                      title={m.senderId}
                    >
                      {senderLabel(m.senderId)}
                    </span>
                    {onManageSender ? (
                      <button
                        type="button"
                        onClick={() => onManageSender(m.senderId)}
                        className="min-h-7 rounded px-1.5 text-[11px] font-medium text-neutral-400 transition-colors hover:bg-danger/5 hover:text-danger focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-400"
                      >
                        차단
                      </button>
                    ) : null}
                  </div>
                ) : null}
                <div
                  className={cn(
                    "rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed break-words",
                    mine
                      ? "rounded-br-sm bg-brand-600 text-white"
                      : "rounded-bl-sm bg-neutral-100 text-neutral-900",
                  )}
                >
                  {m.content}
                </div>
                {!mine ? (
                  <button
                    type="button"
                    onClick={() => {
                      setReportingId(m.id);
                      setReportNotice(undefined);
                    }}
                    className="min-h-9 min-w-11 px-2 text-xs text-neutral-400 transition-colors hover:text-danger sm:opacity-0 sm:transition-opacity sm:group-focus-within:opacity-100 sm:group-hover:opacity-100"
                  >
                    신고
                  </button>
                ) : null}
              </div>
            </div>
          );
        })}
        {reportingId ? (
          <div className="mx-auto flex w-full max-w-md flex-col gap-3 rounded-xl border border-danger/20 bg-danger/5 p-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-semibold text-neutral-900">메시지 신고</p>
              <button
                type="button"
                onClick={() => setReportingId(null)}
                className="text-xs text-neutral-500 hover:text-neutral-900"
              >
                닫기
              </button>
            </div>
            <select
              value={reportReason}
              onChange={(event) => setReportReason(event.target.value as ChatReportReason)}
              className="h-10 rounded-md border border-neutral-200 bg-white px-3 text-sm outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100"
              aria-label="신고 사유"
            >
              <option value="INAPPROPRIATE">욕설·괴롭힘·스팸</option>
              <option value="FRAUD">사기·금전 요구</option>
              <option value="OTHER">기타</option>
            </select>
            <textarea
              value={reportDetail}
              onChange={(event) => setReportDetail(event.target.value)}
              maxLength={1000}
              rows={2}
              placeholder="운영팀이 알아야 할 내용을 적어 주세요 (선택)"
              className="resize-none rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm outline-none focus:border-brand-400 focus:ring-2 focus:ring-brand-100"
            />
            <Button
              type="button"
              size="sm"
              variant="danger"
              disabled={reportBusy}
              onClick={() => void submitReport()}
            >
              {reportBusy ? "접수 중" : "신고 접수"}
            </Button>
          </div>
        ) : null}
        <div ref={bottomRef} />
      </div>

      {/* 입력 영역 */}
      {readOnlyReason === undefined ? (
        <form
          onSubmit={handleSubmit}
          className="flex items-end gap-2 border-t border-neutral-200 px-4 py-3"
        >
          <label htmlFor={messageInputId} className="sr-only">
            메시지 입력
          </label>
          <textarea
            id={messageInputId}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
            placeholder="메시지 입력… (Enter 전송, Shift+Enter 줄바꿈)"
            rows={1}
            className="flex-1 resize-none rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-900 outline-none transition-colors focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
          />
          <Button type="submit" size="sm" disabled={sending || !input.trim()} className="shrink-0">
            전송
          </Button>
        </form>
      ) : (
        <p className="border-t border-neutral-200 bg-neutral-50 px-4 py-3 text-center text-sm text-neutral-600">
          {readOnlyReason}
        </p>
      )}
      {sendError ? (
        <p
          role="alert"
          className="border-t border-danger/10 bg-danger/5 px-4 py-2 text-xs text-danger"
        >
          {sendError}
        </p>
      ) : null}
    </div>
  );
}

function senderLabel(senderId: string): string {
  const compact = senderId.replaceAll("-", "");
  return `사용자 ${compact.slice(0, 8)}`;
}
