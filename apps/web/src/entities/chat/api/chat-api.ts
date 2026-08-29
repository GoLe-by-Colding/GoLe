import { apiRequest } from "@shared/api";
import type {
  ChatMessage,
  ChatReportReason,
  ChatRoom,
  ChatUnreadCounts,
  SocialChatRoom,
} from "../model/types";

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

export function fetchMyRooms(): Promise<readonly ChatRoom[]> {
  return apiRequest<readonly ChatRoom[]>(`${BASE}/rooms`, {
    cache: "no-store",
  });
}

export function fetchMySocialRooms(): Promise<readonly SocialChatRoom[]> {
  return apiRequest<readonly SocialChatRoom[]>(`${BASE}/social/rooms`, { cache: "no-store" });
}

export function fetchUnreadCounts(): Promise<ChatUnreadCounts> {
  return apiRequest<ChatUnreadCounts>(`${BASE}/unread-counts`, { cache: "no-store" });
}

export function markRoomRead(roomId: string, lastMessageId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/rooms/${encodeURIComponent(roomId)}/read`, {
    method: "POST",
    body: { lastMessageId },
  });
}

export function createDirectRoom(peerId: string): Promise<SocialChatRoom> {
  return apiRequest<SocialChatRoom>(`${BASE}/social/rooms/direct`, {
    method: "POST",
    body: { peerId },
  });
}

export function createGroupRoom(
  title: string,
  memberIds: readonly string[],
): Promise<SocialChatRoom> {
  return apiRequest<SocialChatRoom>(`${BASE}/social/rooms/group`, {
    method: "POST",
    body: { title, memberIds },
  });
}

export function createSupportRoom(title: string, message: string): Promise<SocialChatRoom> {
  return apiRequest<SocialChatRoom>(`${BASE}/social/rooms/support`, {
    method: "POST",
    body: { title, message },
  });
}

export interface FetchMessagesOptions {
  readonly beforeSentAt?: string;
  readonly beforeId?: string;
  readonly limit?: number;
}

export function fetchMessages(
  roomId: string,
  options: FetchMessagesOptions = {},
): Promise<readonly ChatMessage[]> {
  const params = new URLSearchParams();
  if (options.beforeSentAt !== undefined) params.set("beforeSentAt", options.beforeSentAt);
  if (options.beforeId !== undefined) params.set("beforeId", options.beforeId);
  if (options.limit !== undefined) params.set("limit", String(options.limit));
  const query = params.size === 0 ? "" : `?${params.toString()}`;
  return apiRequest<readonly ChatMessage[]>(`${BASE}/rooms/${roomId}/messages${query}`, {
    cache: "no-store",
  });
}

export function sendMessage(roomId: string, content: string): Promise<ChatMessage> {
  return apiRequest<ChatMessage>(`${BASE}/rooms/${roomId}/messages`, {
    method: "POST",
    body: { content },
  });
}

export function inviteGroupMember(roomId: string, accountId: string): Promise<SocialChatRoom> {
  return apiRequest<SocialChatRoom>(`${BASE}/social/rooms/${roomId}/members`, {
    method: "POST",
    body: { accountId },
  });
}

export function leaveGroupRoom(roomId: string): Promise<SocialChatRoom> {
  return apiRequest<SocialChatRoom>(`${BASE}/social/rooms/${roomId}/members/me`, {
    method: "DELETE",
  });
}

export function blockChatUser(accountId: string, reason?: string): Promise<void> {
  return apiRequest<void>(`${BASE}/social/blocks/${accountId}`, {
    method: "POST",
    body: reason === undefined ? undefined : { reason },
  });
}

export function fetchBlockedChatUserIds(): Promise<readonly string[]> {
  return apiRequest<readonly string[]>(`${BASE}/social/blocks`, { cache: "no-store" });
}

export function unblockChatUser(accountId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/social/blocks/${accountId}`, { method: "DELETE" });
}

export function reportChatMessage(
  messageId: string,
  reason: ChatReportReason,
  detail?: string,
): Promise<{ readonly id: string }> {
  return apiRequest<{ readonly id: string }>(`${BASE}/messages/${messageId}/report`, {
    method: "POST",
    body: { reason, detail },
  });
}

export function confirmDirectTrade(roomId: string): Promise<ChatRoom> {
  return apiRequest<ChatRoom>(`${BASE}/rooms/${roomId}/direct-trade/confirmation`, {
    method: "POST",
  });
}

export function cancelDirectTradeConfirmation(roomId: string): Promise<ChatRoom> {
  return apiRequest<ChatRoom>(`${BASE}/rooms/${roomId}/direct-trade/confirmation`, {
    method: "DELETE",
  });
}
