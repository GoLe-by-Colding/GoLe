"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { useChatRoom, useRoomReadReceipt } from "@entities/chat";
import { useSession } from "@entities/user";
import { Button, LinkButton, Skeleton } from "@shared/ui";
import { cn } from "@shared/lib";
import { DirectTradeConfirmation } from "./direct-trade-confirmation";

export interface ChatButtonProps {
  readonly listingId: string;
  readonly sellerId: string;
  readonly available: boolean;
  readonly label?: string;
  readonly directTradeEnabled?: boolean;
  readonly initialOpen?: boolean;
}

/**
 * 상품 상세 채팅하기 버튼. 로그인 구매자가 누르면 인라인 채팅 패널을 토글한다.
 * ChatPanel(widget)을 직접 import하면 FSD 위반이라 채팅 UI를 이 feature 안에 인라인한다.
 */
export function ChatButton({
  listingId,
  sellerId,
  available,
  label = "채팅하기",
  directTradeEnabled = true,
  initialOpen = false,
}: ChatButtonProps) {
  const { session } = useSession();
  const [open, setOpen] = useState(initialOpen);

  if (!available) return null;
  if (session?.accountId === sellerId) return null;

  if (!session) {
    return (
      <LinkButton
        href={`/login?returnTo=${encodeURIComponent(`/listings/${listingId}?chat=1`)}`}
        size="lg"
        variant="secondary"
      >
        로그인하고 {label}
      </LinkButton>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <Button size="lg" variant="secondary" onClick={() => setOpen((v) => !v)}>
        {open ? "채팅 닫기" : label}
      </Button>
      {open ? (
        <div
          className="overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-soft"
          style={{ height: 420 }}
        >
          <InlineChatPanel
            key={`${session.accountId}:${listingId}:${sellerId}`}
            listingId={listingId}
            myId={session.accountId}
            sellerId={sellerId}
            directTradeEnabled={directTradeEnabled}
          />
        </div>
      ) : null}
    </div>
  );
}

interface InlineChatPanelProps {
  readonly listingId: string;
  readonly myId: string;
  readonly sellerId: string;
  readonly directTradeEnabled: boolean;
}

function InlineChatPanel({ listingId, myId, sellerId, directTradeEnabled }: InlineChatPanelProps) {
  const { room, messages, send, confirmTrade, cancelTradeConfirmation, retry, loading, error } =
    useChatRoom({
      listingId,
      myId,
      otherId: sellerId,
      isBuyer: true,
    });
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [tradeBusy, setTradeBusy] = useState(false);
  const [actionError, setActionError] = useState<string | undefined>();
  const bottomRef = useRef<HTMLDivElement>(null);
  useRoomReadReceipt({ roomId: room?.id ?? null, myId, messages });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!input.trim() || sending) return;
    setSending(true);
    setActionError(undefined);
    try {
      await send(input.trim());
      setInput("");
    } catch {
      setActionError("메시지를 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSending(false);
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

  const myConfirmation =
    room === null ? null : myId === room.buyerId ? room.buyerConfirmedAt : room.sellerConfirmedAt;

  async function toggleTradeConfirmation() {
    if (tradeBusy || room === null || room.directTradeCompletedAt !== null) return;
    setTradeBusy(true);
    setActionError(undefined);
    try {
      if (myConfirmation === null) await confirmTrade();
      else await cancelTradeConfirmation();
    } catch {
      setActionError("거래 확인 상태를 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setTradeBusy(false);
    }
  }

  return (
    <div className="flex h-full flex-col">
      {room === null ? null : (
        <div className="flex items-center justify-between gap-3 border-b border-neutral-200 bg-neutral-50/70 px-4 py-2.5">
          <span className="text-xs font-semibold text-neutral-600">판매자와 거래 대화</span>
          <LinkButton
            href={`/chat?room=${encodeURIComponent(room.id)}&source=listing`}
            size="sm"
            variant="ghost"
          >
            전체 대화에서 보기
          </LinkButton>
        </div>
      )}
      {directTradeEnabled ? (
        room === null ? null : (
          <DirectTradeConfirmation
            key={room.id}
            room={room}
            myId={myId}
            busy={tradeBusy}
            onToggle={() => void toggleTradeConfirmation()}
          />
        )
      ) : null}
      <div className="flex flex-1 flex-col gap-2 overflow-y-auto p-4">
        {messages.length === 0 ? (
          <p className="text-center text-sm text-neutral-400">
            첫 메시지를 보내 대화를 시작해보세요!
          </p>
        ) : null}
        {messages.map((m) => {
          const mine = m.senderId === myId;
          return (
            <div key={m.id} className={cn("flex", mine ? "justify-end" : "justify-start")}>
              <div
                className={cn(
                  "max-w-[75%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed",
                  mine
                    ? "rounded-br-sm bg-brand-600 text-white"
                    : "rounded-bl-sm bg-neutral-100 text-neutral-900",
                )}
              >
                {m.content}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      <form
        onSubmit={handleSubmit}
        className="flex items-end gap-2 border-t border-neutral-200 px-4 py-3"
      >
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              e.currentTarget.form?.requestSubmit();
            }
          }}
          placeholder="메시지 입력… (Enter 전송)"
          rows={1}
          className="flex-1 resize-none rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm outline-none transition-colors focus-visible:border-brand-400"
        />
        <Button type="submit" size="sm" disabled={sending || !input.trim()} className="shrink-0">
          전송
        </Button>
      </form>
      {actionError ? (
        <p
          role="alert"
          className="border-t border-danger/10 bg-danger/5 px-4 py-2 text-xs text-danger"
        >
          {actionError}
        </p>
      ) : null}
    </div>
  );
}
