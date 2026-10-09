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

/** 이 탭이 옮겨야 할 목적지. `sentIn`은 주입한 문서 번호 — 같은 문서에 두 번 보내지 않는다. */
interface PendingTarget {
  readonly path: string;
  readonly sentIn: number | null;
}

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
  const [tabStartPath, setTabStartPath] = useState(() => validTarget ?? path);
  const startPath = tab === undefined ? path : tabStartPath;
  const source = useMemo(() => ({ uri: origin + startPath }), [origin, startPath]);
  const currentPath = useRef(startPath);
  const handledKey = useRef(targetKey);
  const redirectedUrl = useRef<string | null>(null);
  // 이 WebView가 문서를 한 번이라도 다 불러왔는지, 그리고 지금 새 문서를 불러오는 중인지.
  // 로드 중인 페이지에 넣은 스크립트는 사라질 수 있어 목적지는 둘 다 만족할 때만 옮긴다.
  const loaded = useRef(false);
  const loading = useRef(true);
  // 실제 문서 로드가 시작될 때마다 1씩 는다. 목적지를 보낸 뒤 새 문서가 끝까지 열렸는지 가리는 데 쓴다.
  const documentId = useRef(0);
  // 아직 도착을 확인하지 못한 목적지. 그 목적지의 문서가 성공적으로 열릴 때까지 지우지 않는다 —
  // 로드 전·로드 중이면 기다렸다 보내고, 보낸 로드가 실패하면 다시 시도가 이 목적지를 연다.
  const pending = useRef<PendingTarget | null>(null);
  const pushToken = useDevicePushToken();
  const pushScript = pushToken === null ? null : webPushTokenScript(pushToken);
  const injected = `${appTabScript(tab ?? null)}${pushScript ?? ""}`;

  const openTab = useCallback(
    (request: TabRequest) => {
      const href = {
        pathname: TAB_ROUTE[request.tab],
        params: { to: request.path, at: String(Date.now()) },
      };
      // 탭 화면에서는 탭 내비게이터 안에서 옮기면 된다. 탭이 아닌 화면(알림 상세 `web`)은 루트 Stack에서 `(tabs)`
      // 위에 쌓여 있는데, Expo Router 57의 Stack은 navigate로 아래 `(tabs)`에 돌아가지 않고 새 `(tabs)`를 쌓는다
      // ([기존 tabs, web, 새 tabs] — 작성 중 폼·필터가 처음부터 열린다). dismissTo(POP_TO)는 기존 `(tabs)`까지
      // 내려가 그 키와 중첩 상태를 그대로 두고 params만 바꾸며, 탭 내비게이터가 그 params로 목적지 탭을 연다.
      if (tab === undefined) router.dismissTo(href);
      else router.navigate(href);
    },
    [router, tab],
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

  /** WebView가 지금 보여 주는 같은 원점 경로(경로 + query + hash)를 기록한다. */
  const track = useCallback(
    (url: string) => {
      if (navigationTarget(url, origin) !== "internal") return;
      const parsed = new URL(url);
      currentPath.current = `${parsed.pathname}${parsed.search}${parsed.hash}`;
    },
    [origin],
  );

  /**
   * 로드가 끝나 쉬고 있는 WebView에만 대기 목적지를 보낸다. 한 목적지는 한 번만 보내고 결과를 기다린다.
   * 판단은 보내는 시점의 실제 위치로 한다 — 뿌리만 가리키는 요청은 작성 중인 폼을 지우지 않고 그대로 끝낸다.
   */
  const applyPending = useCallback(() => {
    const request = pending.current;
    if (tab === undefined || request === null || !loaded.current || loading.current) return;
    if (request.sentIn !== null) return;
    if (!shouldMoveTab(request.path, currentPath.current, tab)) {
      pending.current = null;
      return;
    }
    pending.current = { path: request.path, sentIn: documentId.current };
    moveHere(request.path);
  }, [moveHere, tab]);

  /** 새 목적지를 받는다. 이전 목적지가 아직 대기·진행 중이어도 새 요청이 대신한다. */
  const requestPath = useCallback(
    (nextPath: string) => {
      pending.current = { path: nextPath, sentIn: null };
      applyPending();
    },
    [applyPending],
  );

  // 다른 화면이 이 탭으로 목적지를 넘겼을 때. 첫 화면은 startPath가 이미 그 목적지다.
  useEffect(() => {
    if (tab === undefined || validTarget === null || handledKey.current === targetKey) return;
    handledKey.current = targetKey;
    requestPath(validTarget);
  }, [requestPath, tab, targetKey, validTarget]);

  /**
   * WebView가 사라지는 실패 화면으로 바꾼다. 다시 시도하면 새 WebView라 로드 상태도 처음부터다.
   * 보낸 목적지는 지우지 않고 '보내지 않음'으로 되돌려, 다시 시도가 그 목적지를 연다.
   */
  const fail = useCallback(() => {
    loaded.current = false;
    loading.current = true;
    const request = pending.current;
    if (request !== null) pending.current = { path: request.path, sentIn: null };
    setError(true);
  }, []);

  /** 로드가 멈췄다(완료·취소). 이미 문서가 있는 WebView면 대기 중 목적지를 보낸다. */
  const settle = useCallback(() => {
    loading.current = false;
    applyPending();
  }, [applyPending]);

  /**
   * 문서 하나가 성공적으로 열렸다. 대기 목적지에 닿았거나, 목적지를 보낸 뒤 새 문서가 끝까지 열렸으면
   * (웹이 로그인 등으로 다른 곳에 보낸 경우 포함) 그 목적지는 끝난 것이다. 보내지도 닿지도 않았으면 이제 보낸다.
   */
  const finishLoad = useCallback(
    (url: string) => {
      loaded.current = true;
      track(url);
      const request = pending.current;
      if (
        request !== null &&
        (currentPath.current === request.path ||
          (request.sentIn !== null && documentId.current > request.sentIn))
      )
        pending.current = null;
      settle();
    },
    [settle, track],
  );

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
              if (tab !== undefined) {
                // 실패한 곳을 다시 연다 — 대기 목적지가 있으면 그것을, 없으면 실패한 위치를. 뿌리만 가리키는 요청은
                // 실패한 위치가 이미 그 탭 안이면 그 위치를 다시 연다(폼 보존 원칙과 같다).
                const request = pending.current;
                const retryPath =
                  request !== null && shouldMoveTab(request.path, currentPath.current, tab)
                    ? request.path
                    : currentPath.current;
                if (request !== null && retryPath !== request.path) pending.current = null;
                setTabStartPath(retryPath);
              }
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
            if (request.tab === tab) requestPath(request.path);
            else openTab(request);
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
            track(state.url);
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
            if (!nativeEvent.loading) return;
            loading.current = true;
            documentId.current += 1;
          }}
          onLoadProgress={({ nativeEvent }) => {
            // 진행률 1은 로드가 멈췄다는 뜻이다 — 완료뿐 아니라 이 화면이 다른 탭으로 넘기며 막은 이동처럼
            // 취소된 로드(iOS -999는 오류 이벤트가 없다)도 여기서 끝난다.
            if (nativeEvent.progress >= 1) settle();
          }}
          onLoad={({ nativeEvent }) => finishLoad(nativeEvent.url)}
          onError={fail}
          onHttpError={({ nativeEvent }) => {
            // 첫 화면이나 지금 보내 둔 목적지가 서버 오류면 실패 화면으로 바꿔 다시 시도할 수 있게 한다.
            const request = pending.current;
            const sentUrl = request?.sentIn == null ? null : origin + request.path;
            if (
              nativeEvent.statusCode >= 500 &&
              (nativeEvent.url === source.uri || nativeEvent.url === sentUrl)
            )
              fail();
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
