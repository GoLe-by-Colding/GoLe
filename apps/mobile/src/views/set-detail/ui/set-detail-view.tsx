import { useLocalSearchParams, useRouter } from "expo-router";
import { FlatList, StyleSheet, View } from "react-native";
import { formatKrw } from "@gole/core";
import { fetchLegoSetByNumber, isRetired, type LegoSet } from "@gole/core/lego-set";
import { fetchPriceStatistics, type PriceStatistics } from "@gole/core/pricing";
import { fetchListingsBySet, type Listing } from "@gole/core/listing";
import { ListingCard } from "@/entities/listing";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { EmptyState, ErrorState, LoadingState, MediaImage, Screen, Text } from "@/shared/ui";

/** 세트 상세 + 시세 + 이 세트의 매물. 웹 `views/set-detail`에 대응한다. */
export function SetDetailView() {
  const router = useRouter();
  const params = useLocalSearchParams<{ setNumber?: string }>();
  const setNumber = typeof params.setNumber === "string" ? params.setNumber : "";

  const set = useAsync<LegoSet>((signal) => fetchLegoSetByNumber(setNumber, signal), [setNumber]);
  const stats = useAsync<PriceStatistics>(
    (signal) => fetchPriceStatistics(setNumber, signal),
    [setNumber],
  );
  // 이 조회만 AbortSignal 을 받지 않는다 — 웹에서 ISR 캐시를 쓰려고 만든 시그니처다.
  const listings = useAsync<readonly Listing[]>(() => fetchListingsBySet(setNumber), [setNumber]);

  if (set.loading) {
    return (
      <Screen>
        <LoadingState label="세트 정보를 불러오는 중" />
      </Screen>
    );
  }
  if (set.error !== null || set.data === null) {
    return (
      <Screen>
        <ErrorState message={set.error ?? "세트를 찾을 수 없습니다."} onRetry={set.reload} />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={listings.data ?? []}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <SetSummary set={set.data} />
            <PriceBlock stats={stats.data} loading={stats.loading} />
            <Text variant="heading">이 세트의 매물</Text>
          </View>
        }
        ListEmptyComponent={
          listings.loading ? (
            <LoadingState label="매물을 불러오는 중" />
          ) : (
            <EmptyState message="등록된 매물이 없습니다." />
          )
        }
        renderItem={({ item }) => (
          <ListingCard listing={item} onPress={() => router.push(`/listing/${item.id}`)} />
        )}
      />
    </Screen>
  );
}

function SetSummary({ set }: { readonly set: LegoSet }) {
  return (
    <View style={styles.summary}>
      <MediaImage uri={set.imageUrl} width={600} style={styles.cover} contentFit="contain" />
      <Text variant="caption" muted>
        {set.setNumber} · {set.theme}
      </Text>
      <Text variant="title">{set.name}</Text>
      <Text variant="caption" muted>
        {set.pieceCount.toLocaleString("ko-KR")}피스 · {set.releaseYear}년
        {isRetired(set) ? " · 단종" : ""}
      </Text>
    </View>
  );
}

function PriceBlock({
  stats,
  loading,
}: {
  readonly stats: PriceStatistics | null;
  readonly loading: boolean;
}) {
  const colors = useTheme();

  if (loading) {
    return null;
  }
  // 표본이 없으면 숫자를 만들어내지 않는다. "0원"은 시세가 아니다.
  if (stats === null || !stats.hasData) {
    return (
      <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <Text variant="caption" muted>
          아직 체결 기록이 없어 시세를 계산할 수 없습니다.
        </Text>
      </View>
    );
  }
  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <Text variant="caption" muted>
        최근 체결가
      </Text>
      <Text variant="title">{stats.latestPrice === null ? "—" : formatKrw(stats.latestPrice)}</Text>
      <View style={styles.range}>
        <Text variant="caption" muted>
          최고 {stats.highestPrice === null ? "—" : formatKrw(stats.highestPrice)}
        </Text>
        <Text variant="caption" muted>
          최저 {stats.lowestPrice === null ? "—" : formatKrw(stats.lowestPrice)}
        </Text>
        <Text variant="caption" muted>
          거래 {stats.transactionCount}건
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  list: { padding: space[4], gap: space[3] },
  header: { gap: space[4], paddingBottom: space[2] },
  summary: { gap: space[1] },
  cover: { width: "100%", height: 220, borderRadius: radius.xl },
  card: {
    padding: space[4],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    gap: space[1],
  },
  range: { flexDirection: "row", gap: space[4], flexWrap: "wrap" },
});
