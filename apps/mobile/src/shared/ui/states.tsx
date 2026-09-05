import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";
import { radius, space, useTheme } from "@/shared/theme";
import { Text } from "./text";

export function LoadingState({ label = "불러오는 중" }: { readonly label?: string }) {
  const colors = useTheme();
  return (
    <View style={styles.center}>
      <ActivityIndicator color={colors.tint} />
      <Text variant="caption" muted>
        {label}
      </Text>
    </View>
  );
}

export interface ErrorStateProps {
  readonly message: string;
  readonly onRetry?: () => void;
}

/**
 * 네트워크 실패를 빈 화면으로 두지 않는다(R5.4). 재시도는 사용자가 할 수 있는 유일한 행동이므로
 * 항상 손이 닿는 곳에 둔다.
 */
export function ErrorState({ message, onRetry }: ErrorStateProps) {
  const colors = useTheme();
  return (
    <View style={styles.center}>
      <Text variant="body">{message}</Text>
      {onRetry === undefined ? null : (
        <Pressable
          onPress={onRetry}
          style={({ pressed }) => [
            styles.retry,
            { backgroundColor: colors.tint, opacity: pressed ? 0.7 : 1 },
          ]}
        >
          <Text variant="label" style={styles.retryLabel}>
            다시 시도
          </Text>
        </Pressable>
      )}
    </View>
  );
}

export function EmptyState({ message }: { readonly message: string }) {
  return (
    <View style={styles.center}>
      <Text variant="body" muted>
        {message}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: space[3],
    padding: space[6],
  },
  retry: {
    paddingHorizontal: space[5],
    paddingVertical: space[3],
    borderRadius: radius.full,
  },
  retryLabel: { color: "#ffffff" },
});
