export type {
  ChatRoom,
  ChatMessage,
  ChatUnreadCounts,
  ResolvedChatRoom,
  ChatRoomType,
  SocialChatRoom,
  SupportStatus,
  SupportCategory,
  ChatReportReason,
} from "./model/types";
export {
  createOrGetRoom,
  fetchMyRooms,
  fetchChatRoom,
  fetchMySocialRooms,
  fetchUnreadCounts,
  markRoomRead,
  fetchMessages,
  sendMessage,
  createDirectRoom,
  createGroupRoom,
  createSupportRoom,
  inviteGroupMember,
  leaveGroupRoom,
  blockChatUser,
  fetchBlockedChatUserIds,
  unblockChatUser,
  reportChatMessage,
  confirmDirectTrade,
  cancelDirectTradeConfirmation,
} from "./api/chat-api";

// 슬라이스 내부 유틸이었지만 웹의 훅과 앱의 채팅 화면이 함께 쓴다.
// 메시지 병합 규칙이 갈라지면 같은 방이 플랫폼마다 다른 순서로 보인다.
export { chatStreamUrl, mergeChatMessages } from "./model/chat-message-state";
