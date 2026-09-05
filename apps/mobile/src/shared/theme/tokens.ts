/**
 * GoLe 디자인 토큰 — 모바일.
 *
 * <b>단일 출처는 `apps/web/src/app/globals.css`의 `@theme` 블록이다.</b> CSS 변수를 네이티브
 * 런타임에서 읽을 수 없어 값을 복제하지만, 브랜드 색을 바꿀 때는 반드시 양쪽을 함께 고친다.
 * 자동 동기화 파이프라인을 두지 않는 이유는 이 값들이 그만큼 자주 바뀌지 않기 때문이다.
 */

/** GoLe Royal Blue — 레고 클래식 파란색 + 깊은 바다(고래) 톤. */
export const brand = {
  50: "#eff4ff",
  100: "#dbe6fe",
  200: "#bfcffe",
  300: "#93aefb",
  400: "#6082f7",
  500: "#3b5cf2",
  600: "#1d4ed8",
  700: "#1a3fc0",
  800: "#1b359c",
  900: "#1c2f7c",
  950: "#131e4f",
} as const;

/** Brick Gold — 포인트 전용. 레고 옐로보다 골드감. */
export const accent = {
  50: "#fefce8",
  100: "#fef9c3",
  200: "#fef08a",
  300: "#fde047",
  400: "#facc15",
  500: "#eab308",
  600: "#ca8a04",
  700: "#a16207",
} as const;

/** 따뜻한 그레이 — 차갑지 않게. */
export const neutral = {
  50: "#fafafa",
  100: "#f4f4f5",
  200: "#e4e4e7",
  300: "#d4d4d8",
  400: "#a1a1aa",
  500: "#71717a",
  600: "#52525b",
  700: "#3f3f46",
  800: "#27272a",
  900: "#18181b",
} as const;

/**
 * 시세 등락 — 한국 관례(상승 빨강 / 하락 파랑). KREAM·증권앱과 같다.
 *
 * <b>success·danger와 반드시 분리한다.</b> 성공=초록 / 위험=빨강 문법과 시세 문법이 정반대라
 * 같은 토큰을 쓰면 화면마다 의미가 뒤집힌다.
 */
export const market = {
  rise: "#f04452",
  fall: "#3182f6",
} as const;

export const semantic = {
  success: "#16a34a",
  successSoft: "#dcfce7",
  danger: "#dc2626",
  dangerSoft: "#fee2e2",
  warning: "#d97706",
  warningSoft: "#fef3c7",
  info: "#2563eb",
  infoSoft: "#dbeafe",
} as const;

export const radius = {
  sm: 4,
  md: 8,
  lg: 10,
  xl: 14,
  "2xl": 18,
  full: 9999,
} as const;

/** 8px 리듬. 웹의 rem 기반 spacing을 pt로 옮긴 값이다. */
export const space = {
  0.5: 2,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
} as const;

export const fontSize = {
  xs: 12,
  sm: 14,
  base: 16,
  lg: 18,
  xl: 20,
  "2xl": 24,
  "3xl": 30,
} as const;
