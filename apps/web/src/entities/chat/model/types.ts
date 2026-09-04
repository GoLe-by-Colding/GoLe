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

export type SupportCategory =
  | "GENERAL"
  | "TRADE"
  | "PAYMENT"
  | "PRODUCT_FEEDBACK"
  | "PRIVACY_ACCESS"
  | "PRIVACY_CORRECTION_DELETION"
  | "PRIVACY_PROCESSING_STOP";

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
  readonly supportCategory: SupportCategory | null;
  /** 접수 후 3일로 잡은 보수적 내부 진행 경과 안내 목표(법정 기준은 3영업일). */
  readonly progressDueAt: string | null;
  /** 접수 후 10일로 잡은 보수적 내부 결과·처리방안 안내 목표(법정 기준은 10영업일). */
  readonly responseDueAt: string | null;
}

export type ResolvedChatRoom =
  | {
      readonly kind: "LISTING";
      readonly listingRoom: ChatRoom;
      readonly socialRoom: null;
    }
  | {
      readonly kind: "SOCIAL";
      readonly listingRoom: null;
      readonly socialRoom: SocialChatRoom;
    };

export type ChatReportReason = "FRAUD" | "INAPPROPRIATE" | "OTHER";
