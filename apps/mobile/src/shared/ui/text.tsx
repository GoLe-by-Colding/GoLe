import { Text as RNText, type TextProps as RNTextProps, StyleSheet } from "react-native";
import { fontSize, useTheme } from "@/shared/theme";

export type TextVariant = "title" | "heading" | "body" | "caption" | "label";

export interface TextProps extends RNTextProps {
  readonly variant?: TextVariant;
  /** 보조 색(설명·메타 정보). 색을 직접 넘기지 않고 의미로 고른다. */
  readonly muted?: boolean;
}

export function Text({ variant = "body", muted = false, style, ...rest }: TextProps) {
  const colors = useTheme();
  return (
    <RNText
      style={[styles[variant], { color: muted ? colors.textSecondary : colors.text }, style]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize["3xl"], fontWeight: "700", letterSpacing: -0.5 },
  heading: { fontSize: fontSize.xl, fontWeight: "600" },
  body: { fontSize: fontSize.base, fontWeight: "400" },
  caption: { fontSize: fontSize.sm, fontWeight: "400" },
  label: { fontSize: fontSize.xs, fontWeight: "600", letterSpacing: 0.3 },
});
