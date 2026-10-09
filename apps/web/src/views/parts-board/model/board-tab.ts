/**
 * 게시판 탭. `mine`은 로그인했을 때만 보이고, 서버의 `/mine`(상태 무관)을 부른다.
 *
 * 라우트(서버 컴포넌트)가 주소의 `?status=`를 읽을 때도 쓰므로 `"use client"` 파일과 떼어 둔다 —
 * 클라이언트 모듈의 함수는 서버에서 부를 수 없다.
 */
export type PartsBoardTab = "open" | "closed" | "all" | "mine";

export const PARTS_BOARD_TABS: ReadonlyArray<{
  readonly key: PartsBoardTab;
  readonly label: string;
}> = [
  { key: "open", label: "찾는 중" },
  { key: "closed", label: "마감" },
  { key: "all", label: "전체" },
  { key: "mine", label: "내 요청" },
];

/** 주소창 `?status=` 값을 탭으로 읽는다. 모르는 값은 기본 탭(`open`)이다. */
export function parsePartsBoardTab(raw: string | undefined): PartsBoardTab {
  return PARTS_BOARD_TABS.find((tab) => tab.key === raw)?.key ?? "open";
}

/** 필터를 남긴 게시판 주소. 기본 탭(`open`)은 주소에 쓰지 않는다. */
export function partsBoardPath(setNumber: string, tab: PartsBoardTab): string {
  const params = new URLSearchParams();
  if (setNumber.length > 0) params.set("set", setNumber);
  if (tab !== "open") params.set("status", tab);
  const query = params.toString();
  return query.length === 0 ? "/parts" : `/parts?${query}`;
}
