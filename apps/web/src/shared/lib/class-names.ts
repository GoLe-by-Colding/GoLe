/**
 * 조건부 className 조합 유틸. falsy 값은 무시한다.
 */
export type ClassValue = string | number | false | null | undefined;

export function cn(...values: readonly ClassValue[]): string {
  return values.filter((value): value is string | number => Boolean(value)).join(" ");
}
