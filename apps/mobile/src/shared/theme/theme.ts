import { brand, neutral } from "./tokens";

/**
 * 라이트·다크 표면 색. 웹의 `--color-surface-raised`·`--color-text-secondary`를 포함한다.
 * 다크 팔레트는 웹에 대응이 없어 여기서 정한다 — 앱만 시스템 다크모드를 따르기 때문이다.
 */
export interface ThemeColors {
  readonly background: string;
  readonly surface: string;
  readonly surfaceRaised: string;
  readonly border: string;
  readonly text: string;
  readonly textSecondary: string;
  readonly textMuted: string;
  readonly tint: string;
  readonly tabInactive: string;
}

const light: ThemeColors = {
  background: "#ffffff",
  surface: neutral[50],
  surfaceRaised: "#fcfbf8",
  border: neutral[200],
  text: neutral[900],
  textSecondary: "#5b524b",
  textMuted: neutral[500],
  tint: brand[600],
  tabInactive: neutral[400],
};

const dark: ThemeColors = {
  background: neutral[900],
  surface: neutral[800],
  surfaceRaised: neutral[800],
  border: neutral[700],
  text: neutral[50],
  textSecondary: neutral[300],
  textMuted: neutral[400],
  tint: brand[400],
  tabInactive: neutral[500],
};

export const themes = { light, dark } as const;
export type ColorSchemeName = keyof typeof themes;
