import { FlatList, StyleSheet, View } from "react-native";
import { fetchFeed, POST_TOPIC_LABEL, type Post } from "@gole/core/community";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { EmptyState, ErrorState, LoadingState, MediaImage, Screen, Text } from "@/shared/ui";

/** 커뮤니티 피드. 웹 `views/community`에 대응한다. */
export function CommunityView() {
  const result = useAsync<readonly Post[]>((signal) => fetchFeed({ signal, limit: 30 }), []);

  if (result.loading) {
    return (
      <Screen>
        <LoadingState label="글을 불러오는 중" />
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
        <EmptyState message="아직 올라온 글이 없습니다." />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={result.data}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => <PostCard post={item} />}
      />
    </Screen>
  );
}

function PostCard({ post }: { readonly post: Post }) {
  const colors = useTheme();
  return (
    <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <View style={styles.head}>
        <View style={[styles.topic, { backgroundColor: colors.border }]}>
          <Text variant="label" muted>
            {POST_TOPIC_LABEL[post.type] ?? post.type}
          </Text>
        </View>
        <Text variant="caption" muted>
          {formatDate(post.createdAt)}
        </Text>
      </View>
      <Text numberOfLines={6}>{post.content}</Text>
      {post.imageUrls.length > 0 ? (
        <MediaImage uri={post.imageUrls[0]} width={700} style={styles.image} />
      ) : null}
      <Text variant="caption" muted>
        좋아요 {post.likeCount}
      </Text>
    </View>
  );
}

/** 서버는 ISO 문자열을 준다. 목록에서는 날짜까지만 보여준다. */
function formatDate(iso: string): string {
  const at = new Date(iso);
  return Number.isNaN(at.getTime())
    ? ""
    : at.toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
}

const styles = StyleSheet.create({
  list: { padding: space[4], gap: space[3] },
  card: {
    padding: space[4],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    gap: space[2],
  },
  head: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  topic: { paddingHorizontal: space[2], paddingVertical: 2, borderRadius: radius.full },
  image: { width: "100%", height: 200, borderRadius: radius.lg },
});
