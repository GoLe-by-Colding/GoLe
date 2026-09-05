import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { LISTING_CATEGORIES, type ListingCategory, type ListingSort } from "@gole/core/listing";
import { radius, space, useTheme } from "@/shared/theme";
import { Text } from "@/shared/ui";

const SORT_LABEL: Record<ListingSort, string> = {
  newest: "최신순",
  price_asc: "낮은 가격순",
  price_desc: "높은 가격순",
};

const SORTS: readonly ListingSort[] = ["newest", "price_asc", "price_desc"];

export interface ListingFilterBarProps {
  readonly category: ListingCategory | null;
  readonly sort: ListingSort;
  readonly onCategoryChange: (next: ListingCategory | null) => void;
  readonly onSortChange: (next: ListingSort) => void;
}

/** 카테고리·정렬 필터. 가로 스크롤 칩으로 둔다 — 좁은 화면에서 목록을 덜 가린다. */
export function ListingFilterBar({
  category,
  sort,
  onCategoryChange,
  onSortChange,
}: ListingFilterBarProps) {
  return (
    <View style={styles.wrap}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}
      >
        <Chip label="전체" selected={category === null} onPress={() => onCategoryChange(null)} />
        {LISTING_CATEGORIES.map((c) => (
          <Chip
            key={c.key}
            label={c.label}
            selected={category === c.key}
            onPress={() => onCategoryChange(category === c.key ? null : c.key)}
          />
        ))}
      </ScrollView>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}
      >
        {SORTS.map((s) => (
          <Chip
            key={s}
            label={SORT_LABEL[s]}
            selected={sort === s}
            onPress={() => onSortChange(s)}
          />
        ))}
      </ScrollView>
    </View>
  );
}

function Chip({
  label,
  selected,
  onPress,
}: {
  readonly label: string;
  readonly selected: boolean;
  readonly onPress: () => void;
}) {
  const colors = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? colors.tint : colors.surface,
          borderColor: selected ? colors.tint : colors.border,
          opacity: pressed ? 0.75 : 1,
        },
      ]}
    >
      <Text variant="caption" style={selected ? styles.selectedLabel : undefined}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space[2] },
  row: { gap: space[2], paddingHorizontal: space[4] },
  chip: {
    paddingHorizontal: space[3],
    paddingVertical: space[2],
    borderRadius: radius.full,
    borderWidth: StyleSheet.hairlineWidth,
  },
  selectedLabel: { color: "#ffffff" },
});
