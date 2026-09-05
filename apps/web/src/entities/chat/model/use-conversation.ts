"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { fetchMessages, sendMessage } from "@gole/core/chat";
import { chatStreamUrl, mergeChatMessages } from "@gole/core/chat";
import type { ChatMessage } from "@gole/core/chat";

export interface UseConversationResult {
  readonly messages: readonly ChatMessage[];
  readonly send: (content: string) => Promise<void>;
  readonly loadOlder: () => Promise<void>;
  readonly retry: () => void;
  readonly hasOlder: boolean;
  readonly loadingOlder: boolean;
  readonly olderError: string | undefined;
  readonly loading: boolean;
  readonly error: string | undefined;
}

/** 이미 생성된 모든 유형의 방에 공통으로 쓰는 메시지·SSE 훅. */
export function useConversation(roomId: string): UseConversationResult {
  const [messages, setMessages] = useState<readonly ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [olderError, setOlderError] = useState<string | undefined>();
  const [hasOlder, setHasOlder] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [streamCursor, setStreamCursor] = useState<{
    readonly roomId: string;
    readonly afterId: string | undefined;
  } | null>(null);
  const sseRef = useRef<EventSource | null>(null);

  const append = useCallback((message: ChatMessage) => {
    setMessages((current) => mergeChatMessages(current, [message]));
  }, []);

  useEffect(() => {
    let active = true;
    const timer = window.setTimeout(() => {
      if (!active) return;
      setLoading(true);
      setError(undefined);
      setOlderError(undefined);
      setHasOlder(true);
      setLoadingOlder(false);
      setMessages([]);
      setStreamCursor(null);
      void fetchMessages(roomId, { limit: 60 })
        .then((history) => {
          // 이력 요청보다 먼저 도착한 SSE 메시지를 덮어쓰지 않는다.
          if (active) {
            setMessages((current) => mergeChatMessages(history, current));
            setHasOlder(history.length === 60);
            setError(undefined);
            // 멤버십 확인을 겸한 이력 조회가 성공한 뒤에만 자동 재연결 SSE를 연다.
            setStreamCursor({ roomId, afterId: history.at(-1)?.id });
          }
        })
        .catch(() => {
          if (active) {
            setStreamCursor(null);
            setError("대화를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
          }
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [attempt, roomId]);

  useEffect(() => {
    if (streamCursor?.roomId !== roomId) return;
    let active = true;
    let reconcileTimer: number | undefined;
    const eventSource = new EventSource(chatStreamUrl(roomId, streamCursor.afterId), {
      withCredentials: true,
    });
    sseRef.current = eventSource;
    eventSource.addEventListener("message", (event: MessageEvent<string>) => {
      try {
        const data = JSON.parse(event.data) as Omit<ChatMessage, "roomId">;
        append({ ...data, roomId });
        setError(undefined);
      } catch {
        // 손상된 단일 이벤트는 무시하고 다음 이벤트를 계속 받는다.
      }
    });
    eventSource.onerror = () => {
      // EventSource 재연결 사이에 빠진 메시지는 최신 이력과 다시 합쳐 복구한다.
      if (reconcileTimer !== undefined) return;
      reconcileTimer = window.setTimeout(() => {
        reconcileTimer = undefined;
        void fetchMessages(roomId)
          .then((history) => {
            if (active) {
              setMessages((current) => mergeChatMessages(history, current));
              setError(undefined);
            }
          })
          .catch(() => {
            // 스트림 자체 재연결이 계속되므로 일시적인 재조회 실패는 다음 시도로 넘긴다.
          });
      }, 750);
    };
    const periodicReconcile = window.setInterval(() => {
      if (document.visibilityState !== "visible") return;
      void fetchMessages(roomId, { limit: 60 })
        .then((history) => {
          if (active) setMessages((current) => mergeChatMessages(history, current));
        })
        .catch(() => undefined);
    }, 10_000);
    return () => {
      active = false;
      if (reconcileTimer !== undefined) window.clearTimeout(reconcileTimer);
      window.clearInterval(periodicReconcile);
      eventSource.close();
      sseRef.current = null;
    };
  }, [append, roomId, streamCursor]);

  const send = useCallback(
    async (content: string) => {
      const sent = await sendMessage(roomId, content);
      // Redis 장애나 프록시 SSE 지연에도 본인 메시지는 즉시 보인다.
      append(sent);
    },
    [append, roomId],
  );

  const retry = useCallback(() => {
    setAttempt((current) => current + 1);
  }, []);

  const loadOlder = useCallback(async () => {
    const first = messages[0];
    if (first === undefined || loadingOlder || !hasOlder) return;
    setLoadingOlder(true);
    setOlderError(undefined);
    try {
      const older = await fetchMessages(roomId, {
        beforeSentAt: first.sentAt,
        beforeId: first.id,
        limit: 60,
      });
      setMessages((current) => mergeChatMessages(older, current));
      setHasOlder(older.length === 60);
    } catch {
      setOlderError("이전 메시지를 불러오지 못했습니다.");
    } finally {
      setLoadingOlder(false);
    }
  }, [hasOlder, loadingOlder, messages, roomId]);

  return {
    messages,
    send,
    loadOlder,
    retry,
    hasOlder,
    loadingOlder,
    olderError,
    loading,
    error,
  };
}
