"use client";

import { useEffect, useRef } from "react";
import { markRoomRead } from "../api/chat-api";
import type { ChatMessage } from "./types";

export interface UseRoomReadReceiptOptions {
  readonly roomId: string | null;
  readonly myId: string;
  readonly messages: readonly ChatMessage[];
  readonly onRead?: (roomId: string) => void;
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
  const mountedRef = useRef(false);
  const currentRoomRef = useRef(roomId);
  const onReadRef = useRef(onRead);
  const lastIncomingId = messages.findLast((message) => message.senderId !== myId)?.id ?? null;

  useEffect(() => {
    currentRoomRef.current = roomId;
    onReadRef.current = onRead;
  }, [onRead, roomId]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

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

      const promise = markRoomRead(roomId, lastIncomingId)
        .then(() => {
          acknowledgedRef.current = requestKey;
          if (mountedRef.current && currentRoomRef.current === roomId) {
            onReadRef.current?.(roomId);
          }
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
