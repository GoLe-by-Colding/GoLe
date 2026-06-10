import { apiRequest } from "@shared/api";
import type { ChatMessage, ChatRoom } from "../model/types";

const BASE = "/api/v1/chat";

export function createOrGetRoom(
  listingId: string,
  buyerId: string,
  sellerId: string,
): Promise<ChatRoom> {
  return apiRequest<ChatRoom>(`${BASE}/rooms`, {
    method: "POST",
    body: { listingId, buyerId, sellerId },
  });
}

export function fetchMyRooms(userId: string): Promise<readonly ChatRoom[]> {
  return apiRequest<readonly ChatRoom[]>(`${BASE}/rooms?userId=${userId}`, {
    cache: "no-store",
  });
}

export function fetchMessages(roomId: string): Promise<readonly ChatMessage[]> {
  return apiRequest<readonly ChatMessage[]>(`${BASE}/rooms/${roomId}/messages`, {
    cache: "no-store",
  });
}

export function sendMessage(
  roomId: string,
  senderId: string,
  content: string,
): Promise<ChatMessage> {
  return apiRequest<ChatMessage>(`${BASE}/rooms/${roomId}/messages`, {
    method: "POST",
    body: { senderId, content },
  });
}
