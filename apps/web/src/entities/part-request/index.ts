/**
 * `part-request` 엔티티 파사드 — 부족 부품 요청. (wanted-parts)
 *
 * 모델·API는 `@gole/core/parts`에 있다(웹·앱 공유). 코어 모듈 이름이 `parts`인 것은 나중에 부품
 * 카탈로그가 같은 자리로 모이기 때문이다. 웹 슬라이스는 FSD 단수형 규칙(steiger
 * `inconsistent-naming`)을 따라 `part-request`로 둔다. 여기서는 코어를 그대로 다시 내보내고,
 * 이 슬라이스의 웹 전용 부분(표·배지·표시 문구)만 덧붙인다.
 */
export * from "@gole/core/parts";
export { formatRequestedAgo, partRequestTitle, requesterLabel } from "./lib/format";
export { partRequestHref, partRequestsHref } from "./lib/href";
export type { PartRequestsHrefOptions } from "./lib/href";
export { PartItemsTable } from "./ui/part-items-table";
export type { PartItemsTableProps } from "./ui/part-items-table";
export { PartRequestStatusBadge } from "./ui/part-request-status-badge";
export { RequestedAgo } from "./ui/requested-ago";
export type { RequestedAgoProps } from "./ui/requested-ago";
