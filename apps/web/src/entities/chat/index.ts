/**
 * `chat` 엔티티 파사드.
 *
 * 모델·API는 `@gole/core/chat`에 있다(웹·앱 공유). 여기서는 그것을 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분만 덧붙인다. 상위 레이어는 이 경로를 계속 그대로 쓴다.
 */
export * from "@gole/core/chat";
export { useChatRoom } from "./model/use-chat-room";
export type { UseChatRoomOptions, UseChatRoomResult } from "./model/use-chat-room";
export { useConversation } from "./model/use-conversation";
export type { UseConversationResult } from "./model/use-conversation";
export { useRoomReadReceipt } from "./model/use-room-read-receipt";
export type { UseRoomReadReceiptOptions } from "./model/use-room-read-receipt";
