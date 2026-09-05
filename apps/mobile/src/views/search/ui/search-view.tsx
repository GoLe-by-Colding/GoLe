import { useRouter } from "expo-router";
import { useState } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import {
  searchListings,
  type Listing,
  type ListingCategory,
  type ListingSort,
} from "@gole/core/listing";
import { ListingCard } from "@/entities/listing";
import { ListingFilterBar } from "@/features/listing-filter";
import { useAsync } from "@/shared/lib";
import { space } from "@/shared/theme";
import { EmptyState, ErrorState, LoadingState, Screen, Text, TextField } from "@/shared/ui";

/** 검색 — 질의·카테고리·정렬. 웹 `views/search`에 대응한다. */
export function SearchView() {
  const router = useRouter();
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<ListingCategory | null>(null);
  const [sort, setSort] = useState<ListingSort>("newest");

  const result = useAsync<readonly Listing[]>(
    (signal) =>
      searchListings(
        {
          ...(query.length > 0 ? { query } : {}),
          ...(category === null ? {} : { category }),
          sort,
        },
        signal,
      ),
    [query, category, sort],
  );

  return (
    <Screen padded={false}>
      <View style={styles.header}>
        <TextField
          label="검색"
          value={input}
          onChangeText={setInput}
          placeholder="세트 이름·번호로 찾기"
          autoCapitalize="none"
          returnKeyType="search"
          // 글자마다 서버를 때리지 않는다. 확정했을 때만 질의를 바꾼다.
          onSubmitEditing={() => setQuery(input.trim())}
        />
      </View>
      <ListingFilterBar
        category={category}
        sort={sort}
        onCategoryChange={setCategory}
        onSortChange={setSort}
      />
      <Body result={result} onOpen={(id) => router.push(`/listing/${id}`)} />
    </Screen>
  );
}

function Body({
  result,
  onOpen,
}: {
  readonly result: ReturnType<typeof useAsync<readonly Listing[]>>;
  readonly onOpen: (id: string) => void;
}) {
  if (result.loading) {
    return <LoadingState label="매물을 불러오는 중" />;
  }
  if (result.error !== null) {
    return <ErrorState message={result.error} onRetry={result.reload} />;
  }
  if (result.data === null || result.data.length === 0) {
    return <EmptyState message="조건에 맞는 매물이 없습니다." />;
  }
  return (
    <FlatList
      data={result.data}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.list}
      keyboardDismissMode="on-drag"
      ListHeaderComponent={
        <Text variant="caption" muted>
          {result.data.length}건
        </Text>
      }
      renderItem={({ item }) => <ListingCard listing={item} onPress={() => onOpen(item.id)} />}
    />
  );
}

const styles = StyleSheet.create({
  header: { paddingHorizontal: space[4], paddingTop: space[4], paddingBottom: space[3] },
  list: { padding: space[4], gap: space[3] },
});
