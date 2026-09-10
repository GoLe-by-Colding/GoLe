import type { ReactNode } from "react";
import { StyleSheet, View } from "react-native";
import { SafeAreaView, type Edge } from "react-native-safe-area-context";
import { space, useTheme } from "@/shared/theme";

export interface ScreenProps {
  readonly children: ReactNode;
  /**
   * 기본은 인셋 없음. 이 앱의 모든 라우트는 헤더(Stack·Tabs)를 달고 있고 헤더가 이미 상단
   * 인셋을 먹는다 — 여기서 `top`을 또 주면 상태바 높이만큼 빈 띠가 한 번 더 생긴다.
   * 헤더 없이(`headerShown: false`) 띄우는 화면에서만 명시적으로 넘긴다.
   * 하단은 탭 바가 차지하므로 어느 경우에도 기본에서 뺀다.
   */
  readonly edges?: readonly Edge[];
  readonly padded?: boolean;
}

export function Screen({ children, edges = [], padded = true }: ScreenProps) {
  const colors = useTheme();
  return (
    <SafeAreaView edges={[...edges]} style={[styles.root, { backgroundColor: colors.background }]}>
      <View style={padded ? styles.padded : styles.plain}>{children}</View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  plain: { flex: 1 },
  padded: { flex: 1, paddingHorizontal: space[4], paddingTop: space[4] },
});
