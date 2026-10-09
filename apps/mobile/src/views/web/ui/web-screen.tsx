import { useFocusEffect, useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  AppState,
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
import { useDevicePushToken, webPushTokenScript } from "@/features/push-notifications";
import { themes } from "@/shared/theme";
import {
  appNavigationMessage,
  appTabScript,
  navigationTarget,
  resolveWebOrigin,
  shouldMoveTab,
  TAB_PATH,
  TAB_ROUTE,
  tabRequest,
  type AppTab,
  type TabRequest,
} from "../model/navigation";

const colors = themes.light;

/**
 * RN은 탭과 기기 기능, 웹은 서비스 화면과 인증을 소유한다.
 *
 * 탭마다 WebView가 하나라, 홈 탭 안에서 `/search`로 가면 선택된 탭(홈)과 화면(검색)이 어긋난다.
 * 그래서 다른 탭의 뿌리 경로로 가는 탐색은 그 탭으로 넘긴다 — ① 웹 링크는 고정된 이동 메시지로
 * 부탁하고(정확한 원점·두 키·상대 경로만 받는다), ② 전체 페이지 이동은 시작 전에 막고, ③ 웹의
 * 프로그램 이동(`router.push`)은 이미 바뀐 뒤라 그 탭으로 넘기고 이 WebView를 한 단계 되돌린다.
 * 탭이 아닌 화면(알림 상세)은 탭 바가 없어 어긋날 일이 없으니 ①만 따른다.
 */
export function WebScreen({
  path = "/",
  nativeHeader = false,
  tab,
  target,
  targetKey,
}: {
  path?: string;
  nativeHeader?: boolean;
  /** 이 WebView가 맡은 하단 탭. 탭이 아닌 화면은 비운다. */
  tab?: AppTab;
  /** 다른 화면이 이 탭으로 넘긴 목적지(경로 + query + hash). 검증은 여기서 다시 한다. */
  target?: string | undefined;
  /** 같은 목적지를 다시 부탁해도 반응하도록 요청마다 바뀌는 값. */
  targetKey?: string | undefined;
}) {
  const router = useRouter();
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
  const requested = tab === undefined || target === undefined ? null : tabRequest(target, origin);
  const validTarget = requested !== null && requested.tab === tab ? requested.path : null;
  // 탭 화면은 처음 연 경로를 그대로 둔다 — 다시 그릴 때마다 source가 바뀌면 작성 중인 화면이 새로 고쳐진다.
  // 처음 열 때 넘겨받은 목적지가 있으면 그것으로 시작하고, 이후 목적지는 아래 pending이 옮긴다.
  // 탭이 아닌 화면(알림 상세 `web?path=`)은 경로 인자가 바뀌면 그 경로를 연다.
  const [tabStartPath] = useState(() => validTarget ?? path);
  const startPath = tab === undefined ? path : tabStartPath;
  const source = useMemo(() => ({ uri: origin + startPath }), [origin, startPath]);
  const currentPath = useRef(startPath);
  const handledKey = useRef(targetKey);
  const redirectedUrl = useRef<string | null>(null);
  // 이 WebView가 문서를 한 번이라도 다 불러왔는지, 그리고 지금 새 문서를 불러오는 중인지.
  // 로드 중인 페이지에 넣은 스크립트는 사라질 수 있어 목적지는 둘 다 만족할 때만 옮긴다.
  const loaded = useRef(false);
  const loading = useRef(true);
  // 아직 옮기지 못한 목적지. 로드가 끝나면(onLoad) 적용하고, 실패·다시 시도 사이에도 지킨다.
  const pending = useRef<string | null>(null);
  const pushToken = useDevicePushToken();
  const pushScript = pushToken === null ? null : webPushTokenScript(pushToken);
  const injected = `${appTabScript(tab ?? null)}${pushScript ?? ""}`;

  const openTab = useCallback(
    (request: TabRequest) => {
      router.navigate({
        pathname: TAB_ROUTE[request.tab],
        params: { to: request.path, at: String(Date.now()) },
      });
    },
    [router],
  );

  const moveHere = useCallback(
    (nextPath: string) => {
      // 검증한 같은 원점 경로만 JSON 문자열로 이스케이프해 이 WebView에서 연다.
      web.current?.injectJavaScript(
        `window.location.assign(${JSON.stringify(origin + nextPath)});true;`,
      );
    },
    [origin],
  );

  /** 로드가 끝난 WebView에만 대기 중 목적지를 옮긴다. 판단은 적용 시점의 실제 위치로 한다. */
  const applyPending = useCallback(() => {
    const next = pending.current;
    if (tab === undefined || next === null || !loaded.current || loading.current) return;
    pending.current = null;
    if (shouldMoveTab(next, currentPath.current, tab)) moveHere(next);
  }, [moveHere, tab]);

  // 다른 화면이 이 탭으로 목적지를 넘겼을 때. 첫 화면은 startPath가 이미 그 목적지다.
  // 아직 로드 전이면 pending에 남겨 두고 onLoad에서 적용한다 — 주입이 사라져도 목적지는 남는다.
  useEffect(() => {
    if (tab === undefined || validTarget === null || handledKey.current === targetKey) return;
    handledKey.current = targetKey;
    pending.current = validTarget;
    applyPending();
  }, [applyPending, tab, targetKey, validTarget]);

  /** WebView가 사라지는 실패 화면으로 바꾼다. 다시 시도하면 새 WebView라 로드 상태도 처음부터다. */
  const fail = useCallback(() => {
    loaded.current = false;
    loading.current = true;
    setError(true);
  }, []);

  /** 로드가 끝났다(완료·실패·취소). 이미 문서가 있는 WebView면 대기 중 목적지를 옮긴다. */
  const settle = useCallback(() => {
    loading.current = false;
    applyPending();
  }, [applyPending]);

  // 토큰이 페이지를 띄운 뒤에 오면 지금 페이지에 바로 건넨다. 다음 탐색부터는 아래 주입이 맡는다.
  useEffect(() => {
    if (pushScript !== null) web.current?.injectJavaScript(pushScript);
  }, [pushScript]);

  useFocusEffect(
    useCallback(() => {
      // 별도 WebView 탭에서 로그인/로그아웃한 뒤 기존 웹 세션 구독을 깨운다.
      // 입력 중인 폼을 보존하고, 인증 토큰은 읽거나 주입하지 않는다.
      const notifyFocus = () =>
        web.current?.injectJavaScript('window.dispatchEvent(new Event("focus"));true;');
      notifyFocus();
      const appStateListener = AppState.addEventListener("change", (state) => {
        if (state === "active") notifyFocus();
      });
      const listener = BackHandler.addEventListener("hardwareBackPress", () => {
        if (!canGoBack.current) return false;
        web.current?.goBack();
        return true;
      });
      return () => {
        listener.remove();
        appStateListener.remove();
      };
    }, []),
  );

  const openExternal = (url: string) => {
    if (navigationTarget(url, origin) !== "external") return;
    void Linking.openURL(url).catch(() => setLinkError(true));
  };

  return (
    <SafeAreaView
      style={styles.container}
      edges={nativeHeader ? ["left", "right", "bottom"] : ["top", "left", "right"]}
    >
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
              loaded.current = false;
              loading.current = true;
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
          // 웹이 로그인 계정으로 푸시 토큰을 등록한다. 신뢰 원점만 이 WebView 에 뜬다(아래 탐색 정책).
          // 탭 이름과 푸시 토큰은 모두 JSON 상수다. 웹 내용을 실행하거나 세션·쿠키를 읽지 않는다.
          injectedJavaScriptBeforeContentLoaded={injected}
          // 모든 탐색을 아래 정책에서 판정한다. WebView의 자동 외부 앱 열기를 막는다.
          originWhitelist={["*"]}
          onShouldStartLoadWithRequest={(request) => {
            const kind = navigationTarget(request.url, origin);
            if (kind === "internal") {
              // ② 다른 탭의 뿌리로 가는 전체 페이지 이동은 시작 전에 그 탭으로 넘긴다.
              const other = tab === undefined ? null : tabRequest(request.url, origin);
              if (other !== null && other.tab !== tab && request.isTopFrame !== false) {
                openTab(other);
                return false;
              }
              return true;
            }
            if (kind === "external" && request.isTopFrame !== false) openExternal(request.url);
            return false;
          }}
          onMessage={({ nativeEvent }) => {
            // ① 웹 링크가 부탁한 탭 이동. 정확한 원점과 고정된 모양이 아니면 버린다.
            const request = appNavigationMessage(nativeEvent.data, nativeEvent.url, origin);
            if (request === null) return;
            if (request.tab === tab) {
              if (shouldMoveTab(request.path, currentPath.current, request.tab))
                moveHere(request.path);
            } else openTab(request);
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
            const here = tabRequest(state.url, origin);
            if (navigationTarget(state.url, origin) === "internal") {
              const url = new URL(state.url);
              currentPath.current = `${url.pathname}${url.search}${url.hash}`;
            }
            // ③ 웹의 프로그램 이동으로 이미 다른 탭 화면이 됐다. 그 탭으로 넘기고 이 탭은 한 단계 되돌린다.
            if (tab === undefined || here === null || here.tab === tab) {
              redirectedUrl.current = null;
              return;
            }
            if (redirectedUrl.current === state.url) return;
            redirectedUrl.current = state.url;
            openTab(here);
            if (state.canGoBack) web.current?.goBack();
            // 되돌릴 기록이 없으면(첫 화면에서 바로 넘어간 경우) 이 탭의 뿌리로 바꿔 둔다.
            else
              web.current?.injectJavaScript(
                `window.location.replace(${JSON.stringify(origin + TAB_PATH[tab])});true;`,
              );
          }}
          onLoadStart={({ nativeEvent }) => {
            // 실제 문서 로드가 시작될 때만 로드 중으로 본다. Android는 SPA 주소 변화(doUpdateVisitedHistory)도
            // 이 이벤트로 보내지만 진행률이 100이라 loading=false로 온다.
            if (nativeEvent.loading) loading.current = true;
          }}
          onLoadProgress={({ nativeEvent }) => {
            // 진행률 1은 로드가 멈췄다는 뜻이다 — 완료뿐 아니라 이 화면이 다른 탭으로 넘기며 막은 이동처럼
            // 취소된 로드(iOS -999는 오류 이벤트가 없다)도 여기서 끝난다.
            if (nativeEvent.progress >= 1) settle();
          }}
          onLoad={() => {
            loaded.current = true;
            settle();
          }}
          onError={fail}
          onHttpError={({ nativeEvent }) => {
            if (nativeEvent.statusCode >= 500 && nativeEvent.url === source.uri) fail();
          }}
          onContentProcessDidTerminate={fail}
          onRenderProcessGone={fail}
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
