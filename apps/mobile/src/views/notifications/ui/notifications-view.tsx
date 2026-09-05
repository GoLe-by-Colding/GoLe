import { useRouter } from "expo-router";
import { FlatList, Pressable, StyleSheet, View } from "react-native";
import {
  fetchNotifications,
  markNotificationRead,
  type Notification,
} from "@gole/core/notification";
import { useSession } from "@/entities/user";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { Button, EmptyState, ErrorState, LoadingState, Screen, Text } from "@/shared/ui";

/** 알림함. 로그인해야 볼 수 있다. 웹 `views/notifications`에 대응한다. */
export function NotificationsView() {
  const { session } = useSession();

  if (session === null) {
    return <SignedOut />;
  }
  return <SignedIn accountId={session.accountId} />;
}

function SignedOut() {
  const router = useRouter();
  return (
    <Screen>
      <View style={styles.center}>
        <Text variant="title">알림</Text>
        <Text muted>로그인하면 거래·커뮤니티 알림을 볼 수 있습니다.</Text>
        <Button label="로그인" onPress={() => router.push("/sign-in")} />
      </View>
    </Screen>
  );
}

function SignedIn({ accountId }: { readonly accountId: string }) {
  const router = useRouter();
  const result = useAsync<readonly Notification[]>(
    (signal) => fetchNotifications(accountId, signal),
    [accountId],
  );

  if (result.loading) {
    return (
      <Screen>
        <LoadingState label="알림을 불러오는 중" />
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
        <EmptyState message="새 알림이 없습니다." />
      </Screen>
    );
  }

  async function open(item: Notification): Promise<void> {
    if (!item.read) {
      // 읽음 처리가 실패해도 화면 이동은 막지 않는다. 다음 조회에서 다시 시도된다.
      await markNotificationRead(accountId, item.id).catch(() => undefined);
      result.reload();
    }
    // 앱 내부 경로만 따른다. 서버가 준 링크라도 외부 URL을 그대로 열지 않는다.
    if (item.link !== null && item.link.startsWith("/")) {
      router.push(item.link as Parameters<typeof router.push>[0]);
    }
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={result.data}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => <NotificationRow item={item} onPress={() => void open(item)} />}
      />
    </Screen>
  );
}

function NotificationRow({
  item,
  onPress,
}: {
  readonly item: Notification;
  readonly onPress: () => void;
}) {
  const colors = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: item.read ? colors.background : colors.surface,
          borderColor: colors.border,
          opacity: pressed ? 0.8 : 1,
        },
      ]}
    >
      <View style={styles.rowBody}>
        <Text numberOfLines={3}>{item.message}</Text>
        <Text variant="caption" muted>
          {formatWhen(item.createdAt)}
        </Text>
      </View>
      {/* 읽지 않은 것만 점을 찍는다. 목록에서 한눈에 구분되어야 한다. */}
      {item.read ? null : <View style={[styles.dot, { backgroundColor: colors.tint }]} />}
    </Pressable>
  );
}

function formatWhen(iso: string): string {
  const at = new Date(iso);
  return Number.isNaN(at.getTime())
    ? ""
    : at.toLocaleString("ko-KR", {
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", gap: space[3] },
  list: { padding: space[4], gap: space[2] },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: space[3],
    padding: space[4],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
  },
  rowBody: { flex: 1, gap: space[1] },
  dot: { width: 8, height: 8, borderRadius: radius.full },
});
