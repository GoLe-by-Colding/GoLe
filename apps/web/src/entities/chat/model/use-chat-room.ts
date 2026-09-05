"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatMessage, ChatRoom } from "@gole/core/chat";
import {
  cancelDirectTradeConfirmation,
  confirmDirectTrade,
  createOrGetRoom,
  fetchMessages,
  sendMessage,
} from "@gole/core/chat";
import { chatStreamUrl, mergeChatMessages } from "@gole/core/chat";

export interface UseChatRoomOptions {
  readonly listingId: string;
  readonly myId: string;
  readonly otherId: string;
  /** myId가 구매자인지 여부. 구매자=buyerId, 판매자=sellerId. */
  readonly isBuyer: boolean;
}

export interface UseChatRoomResult {
  readonly room: ChatRoom | null;
  readonly messages: readonly ChatMessage[];
  readonly send: (content: string) => Promise<void>;
  readonly confirmTrade: () => Promise<void>;
  readonly cancelTradeConfirmation: () => Promise<void>;
  readonly retry: () => void;
  readonly loading: boolean;
  readonly error: string | undefined;
}

/**
 * 채팅방 상태 관리. 방 생성/조회 → 메시지 이력 로드 → SSE 실시간 수신.
 */
export function useChatRoom({
  listingId,
  myId,
  otherId,
  isBuyer,
}: UseChatRoomOptions): UseChatRoomResult {
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [messages, setMessages] = useState<readonly ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const [attempt, setAttempt] = useState(0);
  const [streamCursor, setStreamCursor] = useState<{
    readonly roomId: string;
    readonly afterId: string | undefined;
  } | null>(null);
  const sseRef = useRef<EventSource | null>(null);

  const appendMessage = useCallback((msg: ChatMessage) => {
    setMessages((current) => mergeChatMessages(current, [msg]));
  }, []);

  useEffect(() => {
    let active = true;
    const buyerId = isBuyer ? myId : otherId;
    const sellerId = isBuyer ? otherId : myId;
    const timer = window.setTimeout(() => {
      if (!active) return;
      // 다른 매물/상대로 전환할 때 이전 방과 스트림을 즉시 분리한다.
      setRoom(null);
      setMessages([]);
      setLoading(true);
      setError(undefined);
      setStreamCursor(null);

      void (async () => {
        try {
          const nextRoom = await createOrGetRoom(listingId, buyerId, sellerId);
          if (!active) return;
          setRoom(nextRoom);
          const history = await fetchMessages(nextRoom.id);
          if (!active) return;
          // 이력 조회 중 먼저 도착한 실시간 메시지를 보존한다.
          setMessages((current) => mergeChatMessages(history, current));
          setError(undefined);
          setStreamCursor({ roomId: nextRoom.id, afterId: history.at(-1)?.id });
        } catch {
          if (active) setError("채팅방을 열 수 없습니다.");
        } finally {
          if (active) setLoading(false);
        }
      })();
    }, 0);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [attempt, listingId, myId, otherId, isBuyer]);

  // 이력 조회로 멤버십을 확인한 뒤 구독한다. 서버가 afterId 이후를 재생해 조회↔구독 틈을 메운다.
  useEffect(() => {
    if (!room || streamCursor?.roomId !== room.id) return;
    let active = true;
    let reconcileTimer: number | undefined;
    // HttpOnly 세션 쿠키로 스트림 참여자를 검증한다. URL에 토큰을 넣어 로그·히스토리에 노출하지 않는다.
    const es = new EventSource(chatStreamUrl(room.id, streamCursor.afterId), {
      withCredentials: true,
    });
    sseRef.current = es;

    es.addEventListener("message", (ev: MessageEvent<string>) => {
      try {
        const data = JSON.parse(ev.data) as {
          id: string;
          senderId: string;
          content: string;
          sentAt: string;
        };
        appendMessage({
          id: data.id,
          roomId: room.id,
          senderId: data.senderId,
          content: data.content,
          sentAt: data.sentAt,
        });
        setError(undefined);
      } catch {
        // 무시
      }
    });

    es.onerror = () => {
      if (reconcileTimer !== undefined) return;
      reconcileTimer = window.setTimeout(() => {
        reconcileTimer = undefined;
        void fetchMessages(room.id)
          .then((history) => {
            if (active) {
              setMessages((current) => mergeChatMessages(history, current));
              setError(undefined);
            }
          })
          .catch(() => {
            // EventSource의 자동 재연결과 다음 재조회 시도에 맡긴다.
          });
      }, 750);
    };
    const periodicReconcile = window.setInterval(() => {
      if (document.visibilityState !== "visible") return;
      void fetchMessages(room.id, { limit: 60 })
        .then((history) => {
          if (active) setMessages((current) => mergeChatMessages(history, current));
        })
        .catch(() => undefined);
    }, 10_000);

    return () => {
      active = false;
      if (reconcileTimer !== undefined) window.clearTimeout(reconcileTimer);
      window.clearInterval(periodicReconcile);
      es.close();
      sseRef.current = null;
    };
  }, [room, appendMessage, streamCursor]);

  const send = useCallback(
    async (content: string) => {
      if (!room) return;
      const sent = await sendMessage(room.id, content);
      // SSE가 지연되거나 끊겨도 본인 메시지는 REST 성공 직후 한 번만 표시한다.
      appendMessage(sent);
    },
    [appendMessage, room],
  );

  const confirmTrade = useCallback(async () => {
    if (!room) return;
    setRoom(await confirmDirectTrade(room.id));
  }, [room]);

  const cancelTradeConfirmation = useCallback(async () => {
    if (!room) return;
    setRoom(await cancelDirectTradeConfirmation(room.id));
  }, [room]);

  const retry = useCallback(() => {
    setAttempt((current) => current + 1);
  }, []);

  return { room, messages, send, confirmTrade, cancelTradeConfirmation, retry, loading, error };
}
