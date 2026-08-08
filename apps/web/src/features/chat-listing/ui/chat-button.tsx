"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { useChatRoom } from "@entities/chat";
import { useSession } from "@entities/user";
import { Button, Skeleton } from "@shared/ui";
import { cn } from "@shared/lib";

export interface ChatButtonProps {
  readonly listingId: string;
  readonly sellerId: string;
  readonly available: boolean;
}

/**
 * 상품 상세 채팅하기 버튼. 로그인 구매자가 누르면 인라인 채팅 패널을 토글한다.
 * ChatPanel(widget)을 직접 import하면 FSD 위반이라 채팅 UI를 이 feature 안에 인라인한다.
 */
export function ChatButton({ listingId, sellerId, available }: ChatButtonProps) {
  const { session } = useSession();
  const [open, setOpen] = useState(false);

  if (!available) return null;
  if (session?.accountId === sellerId) return null;

  if (!session) {
    return (
      <Button size="lg" variant="secondary" disabled>
        채팅하기 (로그인 필요)
      </Button>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <Button size="lg" variant="secondary" onClick={() => setOpen((v) => !v)}>
        {open ? "채팅 닫기" : "채팅하기"}
      </Button>
      {open ? (
        <div
          className="overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-soft"
          style={{ height: 420 }}
        >
          <InlineChatPanel listingId={listingId} myId={session.accountId} sellerId={sellerId} />
        </div>
      ) : null}
    </div>
  );
}

interface InlineChatPanelProps {
  readonly listingId: string;
  readonly myId: string;
  readonly sellerId: string;
}

function InlineChatPanel({ listingId, myId, sellerId }: InlineChatPanelProps) {
  const { messages, send, loading, error } = useChatRoom({
    listingId,
    myId,
    otherId: sellerId,
    isBuyer: true,
  });
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!input.trim() || sending) return;
    setSending(true);
    try {
      await send(input.trim());
      setInput("");
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
  if (error) return <p className="p-4 text-sm text-danger">{error}</p>;

  return (
    <div className="flex h-full flex-col">
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
    </div>
  );
}
