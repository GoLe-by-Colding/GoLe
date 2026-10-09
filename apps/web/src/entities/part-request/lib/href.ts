/**
 * 부품 요청 화면 경로. 게시판·세트 상세·컬렉션·알림이 같은 주소를 쓰도록 한곳에 둔다.
 * 서버 알림 링크(`/parts/{id}`, wanted-parts W8)와 같은 모양이어야 한다.
 */
export function partRequestHref(id: string): string {
  return `/parts/${encodeURIComponent(id)}`;
}

export interface PartRequestsHrefOptions {
  /** 세트 번호. 게시판에서는 필터로, 작성 화면에서는 미리 채울 값으로 쓴다. */
  readonly setNumber?: string | null;
  /** `true`면 작성 화면(`/parts/new`)으로 보낸다. */
  readonly compose?: boolean;
}

/** 게시판(`/parts?set=`) 또는 작성 화면(`/parts/new?set=`) 경로. */
export function partRequestsHref({
  setNumber,
  compose = false,
}: PartRequestsHrefOptions = {}): string {
  const path = compose ? "/parts/new" : "/parts";
  const trimmed = setNumber?.trim() ?? "";
  return trimmed.length === 0 ? path : `${path}?set=${encodeURIComponent(trimmed)}`;
}
