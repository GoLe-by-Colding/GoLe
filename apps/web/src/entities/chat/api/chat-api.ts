import { apiRequest } from "@shared/api";
import type { ChatMessage, ChatRoom } from "../model/types";

const BASE = "/api/v1/chat";

/**
 * 채팅 API. 모든 호출에 세션 토큰이 필요하다 — 서버가 대화 참여자만 통과시킨다.
 *
 * 보낸 사람과 목록 대상은 서버가 세션에서 정하므로 클라이언트가 보내지 않는다.
 */
function auth(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export function createOrGetRoom(
  token: string,
  listingId: string,
  buyerId: string,
  sellerId: string,
): Promise<ChatRoom> {
  return apiRequest<ChatRoom>(`${BASE}/rooms`, {
    method: "POST",
    headers: auth(token),
    body: { listingId, buyerId, sellerId },
  });
}

export function fetchMyRooms(token: string): Promise<readonly ChatRoom[]> {
  return apiRequest<readonly ChatRoom[]>(`${BASE}/rooms`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function fetchMessages(token: string, roomId: string): Promise<readonly ChatMessage[]> {
  return apiRequest<readonly ChatMessage[]>(`${BASE}/rooms/${roomId}/messages`, {
    cache: "no-store",
    headers: auth(token),
  });
}

export function sendMessage(token: string, roomId: string, content: string): Promise<ChatMessage> {
  return apiRequest<ChatMessage>(`${BASE}/rooms/${roomId}/messages`, {
    method: "POST",
    headers: auth(token),
    body: { content },
  });
}

/**
 * SSE 구독 주소.
 *
 * 브라우저 EventSource는 헤더를 붙일 수 없어 토큰을 쿼리로 넘긴다. 서버도 이 엔드포인트에서만
 * 쿼리 토큰을 받는다.
 */
export function chatStreamUrl(base: string, roomId: string, token: string): string {
  return `${base}${BASE}/rooms/${roomId}/stream?token=${encodeURIComponent(token)}`;
}
