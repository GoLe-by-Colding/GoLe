import { useLocalSearchParams, useRouter } from "expo-router";
import { ScrollView, StyleSheet, View } from "react-native";
import { formatKrw } from "@gole/core";
import {
  completenessLabel,
  conditionLabel,
  fetchListingById,
  type Listing,
} from "@gole/core/listing";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { Button, ErrorState, LoadingState, Screen, Text } from "@/shared/ui";
import { ListingGallery } from "./listing-gallery";

/** 매물 상세. 웹 `views/listing-detail`에 대응한다. */
export function ListingDetailView() {
  const params = useLocalSearchParams<{ id?: string }>();
  const id = typeof params.id === "string" ? params.id : "";
  const result = useAsync<Listing>((signal) => fetchListingById(id, signal), [id]);

  if (result.loading) {
    return (
      <Screen>
        <LoadingState label="매물을 불러오는 중" />
      </Screen>
    );
  }
  if (result.error !== null || result.data === null) {
    return (
      <Screen>
        <ErrorState message={result.error ?? "매물을 찾을 수 없습니다."} onRetry={result.reload} />
      </Screen>
    );
  }
  return <Detail listing={result.data} />;
}

function Detail({ listing }: { readonly listing: Listing }) {
  const colors = useTheme();
  const router = useRouter();
  const unavailable = listing.status === "sold" || listing.status === "deleted";

  return (
    <Screen padded={false} edges={["top"]}>
      <ScrollView contentContainerStyle={styles.content}>
        <ListingGallery photoUrls={listing.photoUrls} />

        <View style={styles.section}>
          <Text variant="title">{formatKrw(listing.price)}</Text>
          <Text variant="heading">{listing.title}</Text>
          {listing.status === "reserved" ? (
            <Text variant="caption" muted>
              예약중 — 다른 구매자와 거래가 진행 중입니다.
            </Text>
          ) : null}
          {listing.status === "sold" ? (
            <Text variant="caption" muted>
              판매완료된 매물입니다.
            </Text>
          ) : null}
        </View>

        {/* 구성·하자 고지는 구매 전에 보여야 한다. 접어두지 않는다. */}
        <View
          style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}
        >
          <Row label="상태" value={conditionLabel(listing.condition)} />
          <Row label="구성" value={completenessLabel(listing.completeness)} />
          <Row label="박스" value={listing.hasBox ? "있음" : "없음"} />
          <Row label="설명서" value={listing.hasManual ? "있음" : "없음"} />
          <Row
            label="부품 누락"
            value={listing.hasMissingParts ? listing.missingPartsNote || "있음" : "없음"}
          />
          {listing.defectsNote.length > 0 ? <Row label="하자" value={listing.defectsNote} /> : null}
        </View>

        <View style={styles.section}>
          <Text variant="caption" muted>
            설명
          </Text>
          <Text>{listing.description}</Text>
        </View>

        {listing.catalogSetNumber === null ? null : (
          <View style={styles.section}>
            <Button
              label={`세트 ${listing.catalogSetNumber} 시세 보기`}
              variant="secondary"
              onPress={() => router.push(`/set/${listing.catalogSetNumber ?? ""}`)}
            />
          </View>
        )}

        <View style={styles.section}>
          <Button
            label="판매자 상점 보기"
            variant="secondary"
            onPress={() => router.push(`/seller/${listing.sellerId}`)}
          />
          {/* 거래 문의는 아직 앱에 없다. 없는 기능을 있는 것처럼 보이게 하지 않는다. */}
          <Text variant="caption" muted>
            {unavailable
              ? "종료된 매물이라 문의할 수 없습니다."
              : "거래 문의는 웹에서 이용해 주세요. 앱은 준비 중입니다."}
          </Text>
        </View>
      </ScrollView>
    </Screen>
  );
}

function Row({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <View style={styles.row}>
      <Text variant="caption" muted style={styles.rowLabel}>
        {label}
      </Text>
      <Text variant="caption" style={styles.rowValue}>
        {value}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingBottom: space[10], gap: space[5] },
  section: { paddingHorizontal: space[4], gap: space[2] },
  card: {
    marginHorizontal: space[4],
    padding: space[4],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    gap: space[2],
  },
  row: { flexDirection: "row", gap: space[3] },
  rowLabel: { width: 72 },
  rowValue: { flex: 1 },
});
