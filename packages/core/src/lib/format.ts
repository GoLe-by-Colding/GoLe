/** 원화 표기. 예: 280000 → "₩280,000" */
export function formatKrw(won: number): string {
  return `₩${won.toLocaleString("ko-KR")}`;
}

/**
 * 축·배지처럼 자리가 좁은 곳을 위한 축약 원화 표기. 예: 1392000 → "139.2만"
 *
 * <p>차트 가격축에 `₩1,497,920`처럼 7자리를 그대로 늘어놓으면 눈금 넷이 전부 숫자 벽이 되어
 * 정작 읽어야 할 선을 가린다. 본문·표에서는 정확한 금액이 필요하므로 {@link formatKrw}를 쓴다.
 */
export function formatKrwCompact(won: number): string {
  const abs = Math.abs(won);
  if (abs >= 100_000_000) {
    return `${(won / 100_000_000).toFixed(abs >= 1_000_000_000 ? 0 : 1)}억`;
  }
  if (abs >= 10_000) {
    return `${(won / 10_000).toFixed(abs >= 1_000_000 ? 1 : 0)}만`;
  }
  return won.toLocaleString("ko-KR");
}
