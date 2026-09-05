/**
 * `lego-set` 엔티티 파사드.
 *
 * 모델·API는 `@gole/core/lego-set`에 있다(웹·앱 공유). 여기서는 그것을 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분만 덧붙인다. 상위 레이어는 이 경로를 계속 그대로 쓴다.
 */
export * from "@gole/core/lego-set";
export { LegoSetCard } from "./ui/lego-set-card";
export type { LegoSetCardProps } from "./ui/lego-set-card";
export { OfficialLegoLink } from "./ui/official-lego-link";
export type { OfficialLegoLinkProps } from "./ui/official-lego-link";
