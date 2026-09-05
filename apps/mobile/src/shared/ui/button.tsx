import { ActivityIndicator, Pressable, StyleSheet, type ViewStyle } from "react-native";
import { fontSize, radius, space, useTheme } from "@/shared/theme";
import { Text } from "./text";

export interface ButtonProps {
  readonly label: string;
  readonly onPress: () => void;
  readonly variant?: "primary" | "secondary" | "danger";
  readonly disabled?: boolean;
  readonly loading?: boolean;
  readonly style?: ViewStyle;
}

export function Button({
  label,
  onPress,
  variant = "primary",
  disabled = false,
  loading = false,
  style,
}: ButtonProps) {
  const colors = useTheme();
  // 진행 중에 다시 눌리면 같은 요청이 두 번 나간다. 가입·결제에서 특히 위험하다.
  const blocked = disabled || loading;

  const background =
    variant === "primary" ? colors.tint : variant === "danger" ? "#dc2626" : colors.surface;
  const foreground = variant === "secondary" ? colors.text : "#ffffff";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: blocked, busy: loading }}
      onPress={blocked ? () => undefined : onPress}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: background,
          borderColor: variant === "secondary" ? colors.border : background,
          opacity: blocked ? 0.5 : pressed ? 0.8 : 1,
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={foreground} />
      ) : (
        <Text style={[styles.label, { color: foreground }]}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 48,
    paddingHorizontal: space[5],
    borderRadius: radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: "center",
    justifyContent: "center",
  },
  label: { fontSize: fontSize.base, fontWeight: "600" },
});
