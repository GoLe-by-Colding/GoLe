"use client";

import { type FormEvent, useEffect, useRef, useState } from "react";
import { useChatRoom } from "@entities/chat";
import { Button, Skeleton } from "@shared/ui";
import { cn } from "@shared/lib";

export interface ChatPanelProps {
  readonly listingId: string;
  readonly myId: string;
  readonly otherId: string;
  readonly isBuyer: boolean;
}

/**
 * 실시간 채팅 패널. SSE로 새 메시지를 수신하고 REST로 전송한다.
 */
export function ChatPanel({ listingId, myId, otherId, isBuyer }: ChatPanelProps) {
  const { messages, send, loading, error } = useChatRoom({
    listingId,
    myId,
    otherId,
    isBuyer,
  });

  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지 시 스크롤 말단으로
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
        <Skeleton className="h-8 w-3/4 rounded-xl" />
        <Skeleton className="h-8 w-1/2 rounded-xl" />
      </div>
    );
  }

  if (error) {
    return <p className="p-4 text-sm text-danger">{error}</p>;
  }

  return (
    <div className="flex h-full flex-col">
      {/* 메시지 영역 */}
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

      {/* 입력 영역 */}
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
          placeholder="메시지 입력… (Enter 전송, Shift+Enter 줄바꿈)"
          rows={1}
          className="flex-1 resize-none rounded-xl border border-neutral-200 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus-visible:border-brand-400 focus-visible:ring-2 focus-visible:ring-brand-100"
        />
        <Button type="submit" size="sm" disabled={sending || !input.trim()} className="shrink-0">
          전송
        </Button>
      </form>
    </div>
  );
}
