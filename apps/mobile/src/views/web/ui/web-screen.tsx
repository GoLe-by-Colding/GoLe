import { useFocusEffect } from "expo-router";
import { useCallback, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  BackHandler,
  Linking,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { WebView } from "react-native-webview";
import { themes } from "@/shared/theme";
import { navigationTarget, resolveWebOrigin } from "../model/navigation";

const colors = themes.light;

/** RN은 탭과 기기 기능, 웹은 서비스 화면과 인증을 소유한다. */
export function WebScreen({ path = "/" }: { path?: string }) {
  const web = useRef<WebView>(null);
  const canGoBack = useRef(false);
  const [error, setError] = useState(false);
  const [linkError, setLinkError] = useState(false);
  const [generation, setGeneration] = useState(0);
  const origin = resolveWebOrigin(
    process.env.EXPO_PUBLIC_WEB_BASE_URL,
    __DEV__,
    Platform.OS === "android",
  );
  const source = useMemo(() => ({ uri: origin + path }), [origin, path]);

  useFocusEffect(
    useCallback(() => {
      const listener = BackHandler.addEventListener("hardwareBackPress", () => {
        if (!canGoBack.current) return false;
        web.current?.goBack();
        return true;
      });
      return () => listener.remove();
    }, []),
  );

  const openExternal = (url: string) => {
    if (navigationTarget(url, origin) !== "external") return;
    void Linking.openURL(url).catch(() => setLinkError(true));
  };

  return (
    <SafeAreaView style={styles.container} edges={["top", "left", "right"]}>
      {linkError ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="외부 링크 오류 닫기"
          onPress={() => setLinkError(false)}
        >
          <Text style={styles.message}>외부 앱을 열지 못했습니다. 눌러서 닫기</Text>
        </Pressable>
      ) : null}
      {error ? (
        <View style={styles.failure}>
          <Text accessibilityRole="header" style={styles.title}>
            GoLe 화면을 불러오지 못했습니다
          </Text>
          <Text style={styles.message}>네트워크와 웹 서버 연결을 확인해 주세요.</Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => {
              setError(false);
              canGoBack.current = false;
              setGeneration((value) => value + 1);
            }}
            style={styles.retry}
          >
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        </View>
      ) : (
        <WebView
          key={generation}
          ref={web}
          source={source}
          style={styles.container}
          applicationNameForUserAgent="GoLeApp/1.0"
          // 모든 탐색을 아래 정책에서 판정한다. WebView의 자동 외부 앱 열기를 막는다.
          originWhitelist={["*"]}
          onShouldStartLoadWithRequest={(request) => {
            const target = navigationTarget(request.url, origin);
            if (target === "internal") return true;
            if (target === "external" && request.isTopFrame !== false) openExternal(request.url);
            return false;
          }}
          onOpenWindow={({ nativeEvent }) => {
            if (navigationTarget(nativeEvent.targetUrl, origin) === "internal") {
              // 새 창도 신뢰 원점만 현재 화면에서 연다. URL은 JSON 문자열로 이스케이프한다.
              web.current?.injectJavaScript(
                `window.location.href=${JSON.stringify(nativeEvent.targetUrl)};true;`,
              );
            } else openExternal(nativeEvent.targetUrl);
          }}
          onNavigationStateChange={(state) => {
            canGoBack.current = state.canGoBack;
          }}
          onError={() => setError(true)}
          onHttpError={({ nativeEvent }) => {
            if (nativeEvent.statusCode >= 500 && nativeEvent.url === source.uri) setError(true);
          }}
          onContentProcessDidTerminate={() => setError(true)}
          onRenderProcessGone={() => setError(true)}
          startInLoadingState
          renderLoading={() => (
            <ActivityIndicator
              accessibilityLabel="GoLe 불러오는 중"
              style={styles.loading}
              color={colors.tint}
            />
          )}
          allowsBackForwardNavigationGestures
          allowsInlineMediaPlayback
          mediaPlaybackRequiresUserAction
          javaScriptCanOpenWindowsAutomatically={false}
          sharedCookiesEnabled
          thirdPartyCookiesEnabled={false}
          domStorageEnabled
          allowFileAccess={false}
          allowFileAccessFromFileURLs={false}
          allowUniversalAccessFromFileURLs={false}
          mixedContentMode="never"
          contentInsetAdjustmentBehavior="never"
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  loading: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: colors.background,
  },
  failure: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24, gap: 16 },
  title: { color: colors.text, fontSize: 18, fontWeight: "600", textAlign: "center" },
  message: { color: colors.text, padding: 12, textAlign: "center" },
  retry: {
    backgroundColor: colors.tint,
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 12,
  },
  retryText: { color: "white", fontWeight: "600" },
});
