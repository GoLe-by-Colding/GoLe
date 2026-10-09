/**
 * `bid` 엔티티 파사드 — 구매 입찰. (buy-bids)
 *
 * 모델·API는 `@gole/core/bid`에 있다(웹·앱 공유). 여기서는 그것을 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분(상태 배지)만 덧붙인다.
 */
export * from "@gole/core/bid";
export { BidStatusBadge } from "./ui/bid-status-badge";
