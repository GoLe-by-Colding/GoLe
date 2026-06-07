/** 원화 표기. 예: 280000 → "₩280,000" */
export function formatKrw(won: number): string {
  return `₩${won.toLocaleString("ko-KR")}`;
}
