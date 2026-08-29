export interface ChatRoom {
  readonly id: string;
  readonly listingId: string;
  readonly buyerId: string;
  readonly sellerId: string;
  readonly createdAt: string;
  readonly lastMessageAt: string;
  readonly buyerConfirmedAt: string | null;
  readonly sellerConfirmedAt: string | null;
  readonly directTradeCompletedAt: string | null;
}

export interface ChatMessage {
  readonly id: string;
  readonly roomId: string;
  readonly senderId: string;
  readonly content: string;
  readonly sentAt: string;
}

export type ChatUnreadCounts = Readonly<Record<string, number>>;

export type ChatRoomType = "DIRECT" | "GROUP" | "SUPPORT";

export type SupportStatus = "UNASSIGNED" | "IN_PROGRESS" | "WAITING_USER" | "RESOLVED";

export interface SocialChatRoom {
  readonly id: string;
  readonly type: ChatRoomType;
  readonly memberIds: readonly string[];
  readonly ownerId: string | null;
  readonly title: string | null;
  readonly listingId: null;
  readonly createdAt: string;
  readonly lastMessageAt: string;
  readonly closedAt: string | null;
  readonly supportStatus: SupportStatus | null;
  readonly assigneeId: string | null;
}

export type ChatReportReason = "FRAUD" | "INAPPROPRIATE" | "OTHER";
