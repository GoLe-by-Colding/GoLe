import { Image } from "expo-image";
import { FlatList, StyleSheet, View } from "react-native";
import { formatKrw, thumbnailUrl } from "@gole/core";
import { fetchTrendingSets, type TrendingSet } from "@gole/core/pricing";
import { radius, space, useTheme } from "@/shared/theme";
import { Badge, EmptyState, ErrorState, LoadingState, Screen, Text } from "@/shared/ui";
import { useAsync } from "@/shared/lib";

/** 골드 순위 뱃지를 붙일 상위 구간. 브랜드상 포인트 색은 아껴야 하므로 좁게 잡는다. */
const HIGHLIGHT_RANK = 3;

/**
 * 홈 — 지금 거래가 많은 세트. 웹 `views/home`의 트렌딩 블록에 대응한다.
 *
 * 조회·모델·금액 표기를 모두 `@gole/core`에서 가져온다. 앱이 자기 버전을 갖지 않는다는 것이
 * 이 화면의 요점이다.
 */
export function HomeView() {
  const { data, loading, error, reload } = useAsync((signal) => fetchTrendingSets(12, signal), []);

  if (loading) {
    return (
      <Screen>
        <LoadingState label="인기 세트를 불러오는 중" />
      </Screen>
    );
  }
  if (error !== null) {
    return (
      <Screen>
        <ErrorState message={error} onRetry={reload} />
      </Screen>
    );
  }
  if (data === null || data.length === 0) {
    return (
      <Screen>
        <EmptyState message="아직 거래 기록이 없습니다." />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={data}
        keyExtractor={(item) => item.setNumber}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.header}>
            <Text variant="title">지금 거래 중</Text>
            {/* 웹 히어로와 같은 목소리. 순위의 근거를 밝혀 "인기"가 광고로 읽히지 않게 한다. */}
            <Text variant="caption" muted>
              체결이 많은 순서입니다. 가격은 감이 아니라 체결 기록에서 나옵니다.
            </Text>
          </View>
        }
        renderItem={({ item, index }) => <TrendingRow set={item} rank={index + 1} />}
      />
    </Screen>
  );
}

function TrendingRow({ set, rank }: { readonly set: TrendingSet; readonly rank: number }) {
  const colors = useTheme();
  return (
    <View style={[styles.row, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <Badge label={String(rank)} tone={rank <= HIGHLIGHT_RANK ? "accent" : "neutral"} />
      {set.imageUrl === null ? (
        <View style={[styles.thumb, { backgroundColor: colors.border }]} />
      ) : (
        <Image
          source={{ uri: thumbnailUrl(set.imageUrl, 160) }}
          style={styles.thumb}
          contentFit="contain"
        />
      )}
      <View style={styles.rowText}>
        <Text variant="caption" muted>
          {set.setNumber}
        </Text>
        <Text variant="body" numberOfLines={2}>
          {set.name}
        </Text>
        {/* 시세가 이 앱의 값어치다. 거래 건수와 같은 무게로 두면 그게 안 보인다. */}
        <View style={styles.priceLine}>
          <Text variant="heading">{formatKrw(set.averagePrice)}</Text>
          <Text variant="caption" muted>
            거래 {set.tradeCount}건
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  list: { paddingHorizontal: space[4], paddingBottom: space[8], gap: space[3] },
  header: { paddingVertical: space[4], gap: space[1] },
  row: {
    flexDirection: "row",
    gap: space[3],
    padding: space[3],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: "center",
  },
  thumb: { width: 64, height: 64, borderRadius: radius.md },
  rowText: { flex: 1, gap: space[1] },
  priceLine: { flexDirection: "row", alignItems: "baseline", gap: space[2], flexWrap: "wrap" },
});
