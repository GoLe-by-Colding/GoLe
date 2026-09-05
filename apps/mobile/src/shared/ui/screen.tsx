import type { ReactNode } from "react";
import { StyleSheet, View } from "react-native";
import { SafeAreaView, type Edge } from "react-native-safe-area-context";
import { space, useTheme } from "@/shared/theme";

export interface ScreenProps {
  readonly children: ReactNode;
  /** 탭 화면은 하단을 탭 바가 차지하므로 기본에서 뺀다. */
  readonly edges?: readonly Edge[];
  readonly padded?: boolean;
}

export function Screen({ children, edges = ["top"], padded = true }: ScreenProps) {
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
