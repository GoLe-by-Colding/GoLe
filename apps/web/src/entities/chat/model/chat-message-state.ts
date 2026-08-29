import { env } from "@shared/config";
import type { ChatMessage } from "./types";

/**
 * 이력·REST 응답·SSE 재전송을 하나의 시간순 목록으로 합친다.
 * 뒤에 전달된 메시지를 우선해, 동일 ID의 최신 서버 표현을 유지한다.
 */
export function mergeChatMessages(
  ...batches: readonly (readonly ChatMessage[])[]
): readonly ChatMessage[] {
  const byId = new Map<string, ChatMessage>();
  for (const batch of batches) {
    for (const message of batch) {
      byId.set(message.id, message);
    }
  }

  return [...byId.values()].sort(
    (left, right) => left.sentAt.localeCompare(right.sentAt) || left.id.localeCompare(right.id),
  );
}

/** 브라우저 REST와 같은 API 원점을 사용하는 채팅 SSE 주소. */
export function chatStreamUrl(roomId: string, afterId?: string): string {
  const base = `${env.apiBaseUrl}/api/v1/chat/rooms/${encodeURIComponent(roomId)}/stream`;
  return afterId === undefined ? base : `${base}?afterId=${encodeURIComponent(afterId)}`;
}
