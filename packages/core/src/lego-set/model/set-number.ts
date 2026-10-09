/** 세트 번호로 보는 검색어 — 숫자 3~7자리, 앞의 `#`과 브릭링크식 변형 접미사(`-1`)는 허용한다. */
const SET_NUMBER_TEXT = /^#?(\d{3,7})(?:-\d{1,2})?$/;

/**
 * 검색어가 세트 번호처럼 생겼을 때의 기본 번호 — "75192"·"#75192"·"10307-1" → "75192"·"75192"·"10307".
 * 세트 번호로 보기 어려우면 `null`. 서버 매물 검색(`ListingSearchQuery.setNumberInText`)과 같은 규칙이다.
 */
export function setNumberInSearchText(text: string): string | null {
  return SET_NUMBER_TEXT.exec(text.trim())?.[1] ?? null;
}
