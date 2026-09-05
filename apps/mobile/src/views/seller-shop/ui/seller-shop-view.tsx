import { useLocalSearchParams, useRouter } from "expo-router";
import { FlatList, Pressable, StyleSheet, View } from "react-native";
import { formatKrw } from "@gole/core";
import { conditionLabel } from "@gole/core/listing";
import { fetchSellerShop, type ListingSummary } from "@gole/core/discovery";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { EmptyState, ErrorState, LoadingState, MediaImage, Screen, Text } from "@/shared/ui";

/** 셀러 상점. 웹 `views/seller-shop`에 대응한다. */
export function SellerShopView() {
  const router = useRouter();
  const params = useLocalSearchParams<{ sellerId?: string }>();
  const sellerId = typeof params.sellerId === "string" ? params.sellerId : "";
  const result = useAsync<readonly ListingSummary[]>(
    (signal) => fetchSellerShop(sellerId, signal),
    [sellerId],
  );

  if (result.loading) {
    return (
      <Screen>
        <LoadingState label="상점을 불러오는 중" />
      </Screen>
    );
  }
  if (result.error !== null) {
    return (
      <Screen>
        <ErrorState message={result.error} onRetry={result.reload} />
      </Screen>
    );
  }
  if (result.data === null || result.data.length === 0) {
    return (
      <Screen>
        <EmptyState message="판매 중인 매물이 없습니다." />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={result.data}
        keyExtractor={(item) => item.id}
        numColumns={2}
        columnWrapperStyle={styles.column}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <Text variant="caption" muted>
            판매 중 {result.data.length}건
          </Text>
        }
        renderItem={({ item }) => (
          <SummaryCard summary={item} onPress={() => router.push(`/listing/${item.id}`)} />
        )}
      />
    </Screen>
  );
}

function SummaryCard({
  summary,
  onPress,
}: {
  readonly summary: ListingSummary;
  readonly onPress: () => void;
}) {
  const colors = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: colors.surface, borderColor: colors.border, opacity: pressed ? 0.8 : 1 },
      ]}
    >
      <MediaImage uri={summary.photoUrls[0]} width={300} style={styles.thumb} />
      <View style={styles.body}>
        <Text variant="caption" numberOfLines={2}>
          {summary.title}
        </Text>
        <Text variant="body">{formatKrw(summary.price)}</Text>
        <Text variant="caption" muted>
          {conditionLabel(summary.condition)}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  list: { padding: space[4], gap: space[3] },
  column: { gap: space[3] },
  card: {
    flex: 1,
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: "hidden",
  },
  thumb: { width: "100%", height: 140, borderRadius: 0 },
  body: { padding: space[3], gap: space[1] },
});
