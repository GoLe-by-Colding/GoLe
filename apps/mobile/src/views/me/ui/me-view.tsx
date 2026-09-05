import { useRouter } from "expo-router";
import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { fetchMe, type Me } from "@gole/core/user";
import { useSession } from "@/entities/user";
import { useAsync } from "@/shared/lib";
import { radius, space, useTheme } from "@/shared/theme";
import { Button, LoadingState, Screen, Text } from "@/shared/ui";

/** 내 정보 탭. 비로그인이면 로그인 유도, 로그인 상태면 계정 정보와 로그아웃. */
export function MeView() {
  const { session, signOut } = useSession();

  return session === null ? (
    <SignedOut />
  ) : (
    <SignedIn token={session.sessionToken} onSignOut={signOut} />
  );
}

function SignedOut() {
  const router = useRouter();
  return (
    <Screen>
      <View style={styles.center}>
        <Text variant="title">내 정보</Text>
        <Text muted>로그인하면 매물 관리·찜·채팅을 쓸 수 있습니다.</Text>
        <Button label="로그인" onPress={() => router.push("/sign-in")} style={styles.action} />
        <Button
          label="가입하기"
          variant="secondary"
          onPress={() => router.push("/sign-up")}
          style={styles.action}
        />
        <Button
          label="커뮤니티"
          variant="secondary"
          onPress={() => router.push("/community")}
          style={styles.action}
        />
      </View>
    </Screen>
  );
}

function SignedIn({
  token,
  onSignOut,
}: {
  readonly token: string;
  readonly onSignOut: () => Promise<void>;
}) {
  const colors = useTheme();
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);
  // 세션에는 계정 ID와 권한만 있다. 이메일은 서버에 물어봐야 한다.
  const me = useAsync<Me>(() => fetchMe(token), [token]);

  return (
    <Screen>
      <View style={styles.content}>
        <Text variant="title">내 정보</Text>
        {me.loading ? (
          <LoadingState label="계정 정보를 불러오는 중" />
        ) : (
          <View
            style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.border }]}
          >
            <Text variant="caption" muted>
              이메일
            </Text>
            <Text>{me.data?.email ?? "확인할 수 없음"}</Text>
            {me.data?.role === "ADMIN" ? (
              <Text variant="caption" muted>
                관리자 계정 — 관리 기능은 웹에서 사용합니다.
              </Text>
            ) : null}
          </View>
        )}
        <Button label="알림" variant="secondary" onPress={() => router.push("/notifications")} />
        <Button label="커뮤니티" variant="secondary" onPress={() => router.push("/community")} />
        <Button
          label="로그아웃"
          variant="danger"
          loading={signingOut}
          onPress={() => {
            setSigningOut(true);
            void onSignOut().finally(() => setSigningOut(false));
          }}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, justifyContent: "center", gap: space[3] },
  content: { flex: 1, gap: space[5] },
  action: { alignSelf: "stretch" },
  card: {
    gap: space[1],
    padding: space[4],
    borderRadius: radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
  },
});
