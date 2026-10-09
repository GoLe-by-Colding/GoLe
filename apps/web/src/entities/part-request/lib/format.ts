import type { PartRequest } from "@gole/core/parts";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * 게시판·상세에 쓰는 상대 시각. 일주일이 넘으면 날짜로 바꾼다 — "34일 전"보다 날짜가 읽기 쉽다.
 * 서버 렌더와 브라우저의 시계·시간대가 다르므로 화면에서는 `RequestedAgo`로 브라우저에서만 그린다.
 */
export function formatRequestedAgo(iso: string, now: number = Date.now()): string {
  const at = new Date(iso).getTime();
  if (Number.isNaN(at)) return "";
  const elapsed = Math.max(0, now - at);
  if (elapsed < MINUTE) return "방금 전";
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}분 전`;
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)}시간 전`;
  if (elapsed < 7 * DAY) return `${Math.floor(elapsed / DAY)}일 전`;
  const date = new Date(at);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

/** 요청 한 건을 한 줄로 부른다. 예: "3062b 검정 4개 외 2종". */
export function partRequestTitle(request: Pick<PartRequest, "items">): string {
  const [first, ...rest] = request.items;
  if (first === undefined) return "부품 요청";
  const head = `${first.partNumber} ${first.colorName} ${first.quantity}개`;
  return rest.length === 0 ? head : `${head} 외 ${rest.length}종`;
}

/**
 * 요청자 표시. 닉네임을 계정 ID로 풀어 주는 공개 API가 아직 없어 커뮤니티 글과 같은 방식으로
 * 계정 ID 앞 8자를 쓴다(`widgets/post-card`).
 */
export function requesterLabel(requesterId: string): string {
  return requesterId.slice(0, 8);
}
