import { Pressable, StyleSheet, View } from "react-native";
import { formatKrw } from "@gole/core";
import { conditionLabel, type Listing } from "@gole/core/listing";
import { radius, space, useTheme } from "@/shared/theme";
import { MediaImage, Text } from "@/shared/ui";

export interface ListingCardProps {
  readonly listing: Listing;
  readonly onPress: () => void;
}

/** 매물 카드. 목록 어디서나 같은 모양으로 쓴다. */
export function ListingCard({ listing, onPress }: ListingCardProps) {
  const colors = useTheme();
  const sold = listing.status === "sold";
  const reserved = listing.status === "reserved";

  return (
    <Pressable
      accessibilityRole="button"
      testID="listing-card"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: colors.surface, borderColor: colors.border, opacity: pressed ? 0.8 : 1 },
      ]}
    >
      <MediaImage uri={listing.photoUrls[0]} width={200} style={styles.thumb} />
      <View style={styles.body}>
        <Text variant="body" numberOfLines={2}>
          {listing.title}
        </Text>
        <Text variant="heading">{formatKrw(listing.price)}</Text>
        <View style={styles.meta}>
          <Text variant="caption" muted>
            {conditionLabel(listing.condition)}
          </Text>
          {/* 판매완료·예약중은 값이 아니라 상태다. 가격만 보고 연락하는 일을 막는다. */}
          {sold || reserved ? (
            <View style={[styles.badge, { backgroundColor: colors.border }]}>
              <Text variant="label" muted>
                {sold ? "판매완료" : "예약중"}
              </Text>
            </View>
          ) : null}
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    gap: space[3],
    padding: space[3],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: "center",
  },
  thumb: { width: 88, height: 88, borderRadius: radius.md },
  body: { flex: 1, gap: space[1] },
  meta: { flexDirection: "row", alignItems: "center", gap: space[2] },
  badge: { paddingHorizontal: space[2], paddingVertical: 2, borderRadius: radius.full },
});
