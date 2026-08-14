"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatMessage, ChatRoom } from "../model/types";
import { chatStreamUrl, createOrGetRoom, fetchMessages, sendMessage } from "../api/chat-api";

export interface UseChatRoomOptions {
  readonly listingId: string;
  readonly myId: string;
  readonly otherId: string;
  /** myId가 구매자인지 여부. 구매자=buyerId, 판매자=sellerId. */
  readonly isBuyer: boolean;
  /** 세션 토큰. 채팅은 대화 참여자만 접근할 수 있어 필수다. */
  readonly token: string;
}

export interface UseChatRoomResult {
  readonly room: ChatRoom | null;
  readonly messages: readonly ChatMessage[];
  readonly send: (content: string) => Promise<void>;
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
  token,
}: UseChatRoomOptions): UseChatRoomResult {
  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [messages, setMessages] = useState<readonly ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | undefined>(undefined);
  const sseRef = useRef<EventSource | null>(null);

  const appendMessage = useCallback((msg: ChatMessage) => {
    setMessages((prev) => {
      if (prev.some((m) => m.id === msg.id)) return prev;
      return [...prev, msg];
    });
  }, []);

  useEffect(() => {
    let active = true;
    const buyerId = isBuyer ? myId : otherId;
    const sellerId = isBuyer ? otherId : myId;

    void (async () => {
      try {
        const r = await createOrGetRoom(token, listingId, buyerId, sellerId);
        if (!active) return;
        setRoom(r);
        const history = await fetchMessages(token, r.id);
        if (!active) return;
        setMessages(history);
      } catch {
        if (active) setError("채팅방을 열 수 없습니다.");
      } finally {
        if (active) setLoading(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [listingId, myId, otherId, isBuyer, token]);

  // SSE 연결 — room이 확정된 뒤 구독
  useEffect(() => {
    if (!room) return;
    const base =
      typeof window !== "undefined"
        ? window.location.origin
        : (process.env.NEXT_PUBLIC_API_BASE_URL ?? "");
    const es = new EventSource(chatStreamUrl(base, room.id, token));
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
      } catch {
        // 무시
      }
    });

    return () => {
      es.close();
      sseRef.current = null;
    };
  }, [room, appendMessage, token]);

  const send = useCallback(
    async (content: string) => {
      if (!room) return;
      // 보낸 사람은 서버가 세션에서 정한다.
      await sendMessage(token, room.id, content);
      // 낙관적 업데이트는 SSE에서 처리(중복 dedup)
    },
    [room, token],
  );

  return { room, messages, send, loading, error };
}
