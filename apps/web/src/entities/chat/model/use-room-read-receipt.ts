"use client";

import { useEffect, useRef } from "react";
import { markRoomRead } from "@gole/core/chat";
import type { ChatMessage } from "@gole/core/chat";

export interface UseRoomReadReceiptOptions {
  readonly roomId: string | null;
  readonly myId: string;
  readonly messages: readonly ChatMessage[];
  /** 서버가 읽음을 확정했을 때 부른다. {@code readRequestedAt}은 그 읽음 요청을 보낸 시각(ms)이다. */
  readonly onRead?: (roomId: string, readRequestedAt: number) => void;
}

/** 열린 대화에서 실제로 확인한 마지막 상대 메시지까지만 읽음 처리한다. */
export function useRoomReadReceipt({
  roomId,
  myId,
  messages,
  onRead,
}: UseRoomReadReceiptOptions): void {
  const acknowledgedRef = useRef<string | null>(null);
  const inFlightRef = useRef<{ key: string; promise: Promise<void> } | null>(null);
  const onReadRef = useRef(onRead);
  const lastIncomingId = messages.findLast((message) => message.senderId !== myId)?.id ?? null;

  useEffect(() => {
    onReadRef.current = onRead;
  }, [onRead]);

  useEffect(() => {
    if (roomId === null || lastIncomingId === null) return;
    const requestKey = `${roomId}:${lastIncomingId}`;

    const acknowledge = () => {
      if (
        document.visibilityState !== "visible" ||
        acknowledgedRef.current === requestKey ||
        inFlightRef.current?.key === requestKey
      ) {
        return;
      }

      const readRequestedAt = Date.now();
      const promise = markRoomRead(roomId, lastIncomingId)
        .then(() => {
          acknowledgedRef.current = requestKey;
          // 서버가 확정한 읽음은 응답 전에 방을 닫았더라도 목록에 반영한다. 콜백이 방 ID를 받으므로
          // 다른 방의 수를 지우지 않는다. 막으면 다음 폴링까지 읽은 방이 안 읽음으로 남는다.
          onReadRef.current?.(roomId, readRequestedAt);
        })
        .catch(() => {
          // 서버가 확정하지 않은 읽음 상태는 화면에서도 지우지 않는다.
        })
        .finally(() => {
          if (inFlightRef.current?.key === requestKey) inFlightRef.current = null;
        });
      inFlightRef.current = { key: requestKey, promise };
    };

    acknowledge();
    const handleVisibility = () => acknowledge();
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [lastIncomingId, roomId]);
}
