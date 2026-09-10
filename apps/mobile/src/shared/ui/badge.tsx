import { StyleSheet, View, type ViewStyle } from "react-native";
import { accent, fontSize, neutral, radius, space, useTheme } from "@/shared/theme";
import { Text } from "./text";

/**
 * 짧은 라벨. `accent`(Brick Gold)는 브랜드의 "놀이" 포인트라 화면당 1~2곳으로 아낀다
 * (`.kiro/steering/brand-identity.md`). 그래서 톤을 자유 색상이 아니라 두 가지로 고정한다.
 */
export type BadgeTone = "accent" | "neutral";

export interface BadgeProps {
  readonly label: string;
  readonly tone?: BadgeTone;
  readonly style?: ViewStyle;
}

export function Badge({ label, tone = "neutral", style }: BadgeProps) {
  const colors = useTheme();
  // 골드는 배경으로만 쓴다. 본문 색으로 쓰면 밝은 배경에서 대비가 무너진다.
  const background = tone === "accent" ? accent[400] : colors.surfaceRaised;
  const foreground = tone === "accent" ? neutral[900] : colors.textSecondary;

  return (
    <View
      style={[
        styles.base,
        {
          backgroundColor: background,
          borderColor: tone === "accent" ? accent[500] : colors.border,
        },
        style,
      ]}
    >
      <Text style={[styles.label, { color: foreground }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    minWidth: 26,
    paddingHorizontal: space[2],
    paddingVertical: space[0.5],
    borderRadius: radius.full,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: "center",
    justifyContent: "center",
  },
  label: { fontSize: fontSize.xs, fontWeight: "700", letterSpacing: 0.2 },
});
