/**
 * `offer` 엔티티 파사드 — 가격 제안(네고). (price-offer)
 *
 * 모델·API는 `@gole/core/offer`에 있다(웹·앱 공유). 여기서는 그것을 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분(상태 배지, 매물 제안 조회 훅)만 덧붙인다.
 */
export * from "@gole/core/offer";
export { OfferStatusBadge } from "./ui/offer-status-badge";
export { useListingOffers } from "./model/use-listing-offers";
export type { UseListingOffersOptions, UseListingOffersResult } from "./model/use-listing-offers";
