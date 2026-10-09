// WebScreen 수명주기 회귀 검사. 실행: node .kiro/specs/service-polish/web-screen-lifecycle.test.cjs
// 실제 apps/mobile/src/views/web/ui/web-screen.tsx 컴포넌트를 작은 훅 런타임으로 그려 보고, 경로 인자 갱신·
// 로드 전에 온 목적지·실패 후 다시 시도·폼 보존·메시지·SPA 안전망을 사건 순서대로 흘려 실제 주입/이동을 확인한다.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("../../../apps/mobile/node_modules/typescript");
const repo = path.resolve(__dirname, "../../..");
const O = "https://gole.co.kr";

function load(relative, mocks, globals = {}) {
  const exports = {};
  const source = ts.transpileModule(fs.readFileSync(path.join(repo, relative), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  vm.runInNewContext(source, { exports, URL, JSON, Date, String, ...globals, require: (name) => {
    if (!(name in mocks)) throw new Error(`Unexpected import: ${name}`);
    return mocks[name];
  }});
  return exports;
}

// ── 작은 훅 런타임: 상태·참조·메모·효과(의존성 비교·정리)와 상태 변경 시 다시 그리기 ──
function createHarness() {
  let hooks = [], index = 0, dirty = false, queue = [], Component = null, props = null, tree = null;
  const sameDeps = (a, b) => a && b && a.length === b.length && a.every((v, i) => Object.is(v, b[i]));
  const React = {
    useState(init) {
      const i = index++;
      if (!(i in hooks)) hooks[i] = { value: typeof init === "function" ? init() : init };
      const slot = hooks[i];
      return [slot.value, (v) => {
        const next = typeof v === "function" ? v(slot.value) : v;
        if (!Object.is(next, slot.value)) { slot.value = next; dirty = true; }
      }];
    },
    useRef(init) { const i = index++; if (!(i in hooks)) hooks[i] = { current: init }; return hooks[i]; },
    useMemo(fn, deps) {
      const i = index++;
      if (!(i in hooks) || !sameDeps(hooks[i].deps, deps)) hooks[i] = { value: fn(), deps };
      return hooks[i].value;
    },
    useCallback(fn, deps) { return React.useMemo(() => fn, deps); },
    useEffect(fn, deps) {
      const i = index++;
      const prev = hooks[i];
      if (prev && deps && sameDeps(prev.deps, deps)) return;
      hooks[i] = { deps, cleanup: prev?.cleanup };
      queue.push(() => { hooks[i].cleanup?.(); const c = fn(); hooks[i].cleanup = typeof c === "function" ? c : undefined; });
    },
  };
  const webviews = [];
  let mountedKey;
  function find(node, type) {
    if (node === null || typeof node !== "object") return null;
    if (Array.isArray(node)) { for (const n of node) { const f = find(n, type); if (f) return f; } return null; }
    if (node.type === type) return node;
    return find(node.props?.children, type);
  }
  function commit() {
    const web = find(tree, "WebView");
    if (web === null) { mountedKey = undefined; return; }
    if (web.key !== mountedKey) {
      mountedKey = web.key;
      webviews.push({ key: web.key, injected: [], back: 0, injectJavaScript(js) { this.injected.push(js); }, goBack() { this.back++; } });
    }
    if (web.props.ref) web.props.ref.current = webviews[webviews.length - 1];
  }
  function render() {
    do {
      dirty = false; index = 0; queue = [];
      tree = Component(props);
      commit();
      for (const run of queue) run();
    } while (dirty);
  }
  return {
    React,
    mount(C, p) { Component = C; props = p; render(); },
    update(p) { props = { ...props, ...p }; render(); },
    act(fn) { const r = fn(); render(); return r; },
    web() { return find(tree, "WebView"); },
    tree() { return tree; },
    instance() { return webviews[webviews.length - 1]; },
    instances: webviews,
    find(type) { return find(tree, type); },
  };
}

const shared = load("apps/mobile/src/shared/lib/notification-route.ts", {});
const navigation = load("apps/mobile/src/views/web/model/navigation.ts", { "@/shared/lib": shared });
let checks = 0;
const ok = (cond, label) => { assert.ok(cond, label); checks++; };
// vm 안에서 만든 객체·배열은 프로토타입이 달라 엄격 비교가 실패하므로 값만 비교한다.
const plain = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)));
const eq = (a, b, label) => { assert.deepEqual(plain(a), plain(b), label); checks++; };

function screen(initialProps) {
  const h = createHarness();
  const navigated = [], opened = [];
  const mod = load("apps/mobile/src/views/web/ui/web-screen.tsx", {
    react: h.React,
    "react/jsx-runtime": { jsx: (type, props, key) => ({ type, props, key }), jsxs: (type, props, key) => ({ type, props, key }), Fragment: "Fragment" },
    "react-native": {
      ActivityIndicator: "ActivityIndicator", Pressable: "Pressable", Text: "Text", View: "View",
      StyleSheet: { create: (x) => x }, Platform: { OS: "ios" },
      AppState: { addEventListener: () => ({ remove() {} }) },
      BackHandler: { addEventListener: () => ({ remove() {} }) },
      Linking: { openURL: (url) => { opened.push(url); return Promise.resolve(); } },
    },
    "react-native-safe-area-context": { SafeAreaView: "SafeAreaView" },
    "react-native-webview": { WebView: "WebView" },
    "expo-router": {
      useRouter: () => ({
        navigate: (to) => navigated.push({ via: "navigate", ...to }),
        dismissTo: (to) => navigated.push({ via: "dismissTo", ...to }),
      }),
      useFocusEffect: (cb) => h.React.useEffect(() => cb(), [cb]),
    },
    "@/features/push-notifications": { useDevicePushToken: () => null, webPushTokenScript: () => "" },
    "@/shared/theme": { themes: { light: { background: "#fff", tint: "#1d4ed8", text: "#111", tabInactive: "#999", border: "#eee" } } },
    "../model/navigation": navigation,
  }, { process: { env: { EXPO_PUBLIC_WEB_BASE_URL: O } }, __DEV__: false });
  h.mount(mod.WebScreen, initialProps);
  const fire = (name, event) => h.act(() => h.web().props[name](event));
  // 문서 하나가 끝까지 열렸다(react-native-webview onLoad의 nativeEvent.url).
  const loadedAt = (path) => fire("onLoad", { nativeEvent: { url: O + path, loading: false } });
  // 실제 문서 로드가 시작됐다(onLoadStart loading=true + 같은 주소의 내비게이션 상태 변화).
  const startLoad = (path) => {
    fire("onLoadStart", { nativeEvent: { url: O + path, loading: true } });
    fire("onNavigationStateChange", { url: O + path, canGoBack: true });
  };
  const retry = () => h.act(() => h.find("Pressable").props.onPress());
  return { h, navigated, opened, fire, loadedAt, startLoad, retry };
}
const assigns = (instance) => instance.injected.filter((js) => js.startsWith("window.location.assign("));
const assign = (path) => `window.location.assign(${JSON.stringify(O + path)});true;`;

// 1. 탭이 아닌 화면은 경로 인자가 바뀌면 그 경로를 연다(예전에는 첫 값에 고정됐다).
{
  const { h } = screen({ path: "/notifications", nativeHeader: true });
  eq(h.web().props.source, { uri: `${O}/notifications` }, "비탭 첫 경로");
  h.update({ path: "/orders/o1#review" });
  eq(h.web().props.source, { uri: `${O}/orders/o1#review` }, "비탭 경로 갱신을 따른다");
  eq(h.instances.length, 1, "같은 WebView에서 source만 바뀐다");
}

// 2. 탭 첫 마운트에 목적지가 있으면 그 경로로 시작하고 주입하지 않는다. 이후 다시 그려도 source는 고정이다.
{
  const { h } = screen({ path: "/chat", tab: "chat", target: "/chat?room=r1", targetKey: "k1" });
  eq(h.web().props.source, { uri: `${O}/chat?room=r1` }, "목적지로 시작");
  h.update({ path: "/chat" });
  eq(h.web().props.source, { uri: `${O}/chat?room=r1` }, "탭 source는 다시 그려도 고정");
  eq(assigns(h.instance()), [], "첫 목적지는 주입하지 않는다");
  eq(h.web().props.injectedJavaScriptBeforeContentLoaded.startsWith('window.__GOLE_APP_TAB__="chat";'), true, "탭 표시 주입");
}

// 3. 느린 로드 — 로드 전에 온 목적지는 사라지지 않고 onLoad에서 한 번만 적용된다.
{
  const { h, loadedAt, startLoad } = screen({ path: "/search", tab: "search" });
  h.update({ target: "/search?query=%EB%A0%88%EA%B3%A0#top", targetKey: "k2" });
  eq(assigns(h.instance()), [], "로드 전에는 주입하지 않는다");
  loadedAt("/search");
  eq(assigns(h.instance()), [assign("/search?query=%EB%A0%88%EA%B3%A0#top")], "onLoad에서 query·hash 그대로 한 번 적용");
  startLoad("/search?query=%EB%A0%88%EA%B3%A0#top");
  loadedAt("/search?query=%EB%A0%88%EA%B3%A0#top");
  h.update({ targetKey: "k2" });
  eq(assigns(h.instance()).length, 1, "다음 로드·같은 요청 키로는 다시 옮기지 않는다");
}

// 4. 로드 뒤에 온 목적지는 바로 옮긴다. 다른 탭의 경로·외부 원점은 무시한다.
{
  const { h, loadedAt } = screen({ path: "/search", tab: "search" });
  loadedAt("/search");
  h.update({ target: "/search?query=x", targetKey: "k3" });
  eq(assigns(h.instance()).length, 1, "로드 뒤 즉시 적용");
  h.update({ target: "https://evil.test/search", targetKey: "k4" });
  h.update({ target: "/chat?room=1", targetKey: "k5" });
  h.update({ target: "//evil.test/search", targetKey: "k6" });
  eq(assigns(h.instance()).length, 1, "신뢰 원점이 아니거나 다른 탭 경로면 옮기지 않는다");
}

// 5. 폼 보존 — 뿌리만 가리키는 요청은 탭이 자기 영역에 있으면 옮기지 않고, 대기열도 비운다.
{
  const { h, fire, loadedAt } = screen({ path: "/sell", tab: "sell" });
  loadedAt("/sell");
  fire("onNavigationStateChange", { url: `${O}/sell?step=2`, canGoBack: true });
  h.update({ target: "/sell", targetKey: "k7" });
  loadedAt("/sell?step=2");
  eq(assigns(h.instance()), [], "작성 중인 /sell?step=2를 새로 고치지 않는다");
}

// 6. 첫 로드 실패 사이에 온 목적지는 다시 시도가 곧바로 그 목적지로 연다(한 번의 로드, 주입 없음).
{
  const { h, fire, loadedAt, retry } = screen({ path: "/chat", tab: "chat" });
  fire("onError", {});
  ok(h.web() === null, "실패 화면으로 바뀌면 WebView가 내려간다");
  h.update({ target: "/chat?room=r9", targetKey: "k8" });
  retry();
  ok(h.web() !== null && h.instances.length === 2, "다시 시도는 새 WebView");
  eq(h.web().props.source, { uri: `${O}/chat?room=r9` }, "다시 시도는 대기 목적지를 연다");
  loadedAt("/chat?room=r9");
  eq(assigns(h.instance()), [], "목적지에 닿았으니 주입하지 않는다");
}

// 7. 웹 메시지 — 정확한 원점·고정 모양만 탭 이동으로 바뀐다. 탭 화면은 탭 안에서 navigate로 옮긴다.
{
  const { navigated, fire, loadedAt } = screen({ path: "/", tab: "home" });
  loadedAt("/");
  fire("onMessage", { nativeEvent: { data: JSON.stringify({ type: "gole:navigate", path: "/sell?draft=1" }), url: `${O}/` } });
  eq(navigated.length, 1, "다른 탭 요청은 그 탭으로");
  eq([navigated[0].via, navigated[0].pathname, navigated[0].params.to], ["navigate", "/sell", "/sell?draft=1"], "탭 화면은 navigate, 탭 경로와 query 보존");
  fire("onMessage", { nativeEvent: { data: JSON.stringify({ type: "gole:navigate", path: "/sell" }), url: "https://evil.test/" } });
  fire("onMessage", { nativeEvent: { data: JSON.stringify({ type: "gole:navigate", path: "/sell", js: "x" }), url: `${O}/` } });
  eq(navigated.length, 1, "다른 원점·다른 모양은 버린다");
}

// 8. 전체 페이지 이동 — 다른 탭 뿌리는 시작 전에 막고 넘긴다. 같은 탭은 허용, 외부는 외부 앱.
{
  const { navigated, opened, h } = screen({ path: "/", tab: "home" });
  const should = (url, isTopFrame = true) => h.web().props.onShouldStartLoadWithRequest({ url, isTopFrame });
  eq(should(`${O}/listings/abc`), true, "탭 밖 경로는 이 탭에서");
  eq(should(`${O}/chat?room=r2`), false, "다른 탭 뿌리는 막는다");
  eq(navigated.map((n) => n.params.to), ["/chat?room=r2"], "막은 이동은 그 탭으로");
  eq(should("https://pg.example/pay"), false, "외부는 WebView에서 열지 않는다");
  eq(opened, ["https://pg.example/pay"], "외부는 외부 앱으로");
}

// 9. SPA 안전망 — 이미 바뀐 다른 탭 화면은 그 탭으로 넘기고 이 탭을 되돌린다. 기록이 없으면 뿌리로 바꾼다.
{
  const { navigated, fire, h, loadedAt } = screen({ path: "/profile", tab: "me" });
  loadedAt("/profile");
  fire("onNavigationStateChange", { url: `${O}/`, canGoBack: true });
  fire("onNavigationStateChange", { url: `${O}/`, canGoBack: true });
  eq([navigated.length, h.instance().back], [1, 1], "같은 주소 중복 이벤트는 한 번만");
  fire("onNavigationStateChange", { url: `${O}/profile`, canGoBack: false });
  fire("onNavigationStateChange", { url: `${O}/search?query=z`, canGoBack: false });
  eq(navigated.map((n) => n.params.to), ["/", "/search?query=z"], "되돌린 뒤 새 이동도 넘긴다");
  ok(h.instance().injected.includes(`window.location.replace(${JSON.stringify(`${O}/profile`)});true;`), "기록이 없으면 이 탭 뿌리로 바꾼다");
}

// 10. 두 번째 전체 로드 중에 온 목적지 — 로드가 끝날 때(onLoad) 적용한다. 진행 중 진행률로는 옮기지 않는다.
{
  const { h, fire, loadedAt, startLoad } = screen({ path: "/search", tab: "search" });
  loadedAt("/search");
  startLoad("/listings/abc");
  h.update({ target: "/search?query=during-load", targetKey: "k9" });
  fire("onLoadProgress", { nativeEvent: { progress: 0.4 } });
  eq(assigns(h.instance()), [], "새 문서를 불러오는 중에는 주입하지 않는다");
  loadedAt("/listings/abc");
  eq(assigns(h.instance()), [assign("/search?query=during-load")], "로드가 끝나면 적용");
}

// 11. 취소된 로드 — 다른 탭으로 넘기며 막은 이동은 onLoad 없이 진행률 1로 끝난다. 그때 대기 목적지를 옮긴다.
{
  const { h, fire, navigated, loadedAt, startLoad } = screen({ path: "/", tab: "home" });
  loadedAt("/");
  startLoad("/prices");
  h.update({ target: "/?section=trending", targetKey: "k10" });
  eq(h.web().props.onShouldStartLoadWithRequest({ url: `${O}/chat?room=r3`, isTopFrame: true }), false, "리다이렉트로 온 다른 탭 이동을 막는다");
  eq(navigated.map((n) => n.params.to), ["/chat?room=r3"], "막은 이동은 채팅 탭으로");
  eq(assigns(h.instance()), [], "취소가 확인되기 전에는 옮기지 않는다");
  fire("onLoadProgress", { nativeEvent: { progress: 1 } });
  eq(assigns(h.instance()), [assign("/?section=trending")], "진행률 1(로드 멈춤)에서 적용");
}

// 12. Android SPA 주소 변화는 onLoadStart가 loading=false로 온다 — 로드 중으로 보지 않고 목적지를 바로 옮긴다.
{
  const { h, fire, loadedAt } = screen({ path: "/chat", tab: "chat" });
  loadedAt("/chat");
  fire("onLoadStart", { nativeEvent: { url: `${O}/chat?room=r1`, loading: false } });
  h.update({ target: "/chat?room=r2", targetKey: "k11" });
  eq(assigns(h.instance()).length, 1, "SPA 이동 뒤에도 바로 적용");
}

// 13. 탭이 아닌 화면(알림 상세)의 탭 이동은 dismissTo로 기존 탭 스택까지 내려가며 목적지(query·hash)를 넘긴다.
{
  const { navigated, fire, loadedAt } = screen({ path: "/notifications", nativeHeader: true });
  loadedAt("/notifications");
  fire("onMessage", { nativeEvent: { data: JSON.stringify({ type: "gole:navigate", path: "/sell?draft=1#top" }), url: `${O}/notifications` } });
  eq([navigated.length, navigated[0].via, navigated[0].pathname, navigated[0].params.to], [1, "dismissTo", "/sell", "/sell?draft=1#top"], "비탭은 dismissTo");
}

// 14. 보낸 목적지의 로드가 실패하면 다시 시도가 그 목적지를 연다(리뷰 재현: /chat → r2 실패 → 다시 시도가 /chat로 돌아가던 결함).
{
  const { h, fire, loadedAt, startLoad, retry } = screen({ path: "/chat", tab: "chat" });
  loadedAt("/chat");
  h.update({ target: "/chat?room=r2", targetKey: "k20" });
  eq(assigns(h.instance()), [assign("/chat?room=r2")], "목적지를 한 번 보낸다");
  startLoad("/chat?room=r2");
  fire("onError", {});
  retry();
  eq(h.web().props.source, { uri: `${O}/chat?room=r2` }, "다시 시도는 실패한 목적지 r2를 연다");
  eq(assigns(h.instance()), [], "새 WebView는 source로 바로 연다(주입 없음)");
  // 15. 다시 실패해도 목적지를 지킨다.
  startLoad("/chat?room=r2");
  fire("onError", {});
  retry();
  eq([h.instances.length, h.web().props.source.uri], [3, `${O}/chat?room=r2`], "재실패 뒤 다시 시도도 r2");
  startLoad("/chat?room=r2");
  loadedAt("/chat?room=r2");
  loadedAt("/chat?room=r2");
  fire("onLoadProgress", { nativeEvent: { progress: 1 } });
  eq(assigns(h.instance()), [], "도착한 뒤에는 다시 보내지 않는다(루프 없음)");
}

// 14b. 주소가 바뀌기 전에 끝난 실패(예비 탐색 실패)도 목적지를 잃지 않는다 — 실패한 위치가 아직 /chat로 보여도 r2를 연다.
{
  const { h, fire, loadedAt, retry } = screen({ path: "/chat", tab: "chat" });
  loadedAt("/chat");
  h.update({ target: "/chat?room=r2", targetKey: "k25" });
  fire("onLoadStart", { nativeEvent: { url: `${O}/chat`, loading: true } });
  fire("onError", {});
  retry();
  eq(h.web().props.source, { uri: `${O}/chat?room=r2` }, "보낸 목적지는 실패한 위치보다 앞선다");
}

// 16. 실패 화면에 있는 동안 온 새 요청은 이전 요청을 대신한다.
{
  const { h, fire, loadedAt, startLoad, retry } = screen({ path: "/chat", tab: "chat" });
  loadedAt("/chat");
  h.update({ target: "/chat?room=r2", targetKey: "k30" });
  startLoad("/chat?room=r2");
  fire("onError", {});
  h.update({ target: "/chat?room=r3", targetKey: "k31" });
  retry();
  eq(h.web().props.source, { uri: `${O}/chat?room=r3` }, "다시 시도는 최신 요청 r3를 연다");
}

// 17. 보낸 목적지는 결과가 나올 때까지 같은 문서에 다시 보내지 않는다(iOS SPA onLoad·진행률 1에도).
{
  const { h, fire, loadedAt, startLoad } = screen({ path: "/chat", tab: "chat" });
  loadedAt("/chat");
  h.update({ target: "/chat?room=r4", targetKey: "k40" });
  loadedAt("/chat");
  fire("onLoadProgress", { nativeEvent: { progress: 1 } });
  eq(assigns(h.instance()).length, 1, "중복 주입 없음");
  startLoad("/chat?room=r4");
  loadedAt("/chat?room=r4");
  h.update({ target: "/chat?room=r4", targetKey: "k41" });
  eq(assigns(h.instance()).length, 1, "이미 그 방이면 같은 요청은 옮기지 않는다");
}

// 18. 보낸 목적지가 서버 오류(5xx)면 실패 화면으로 바꾸고 다시 시도가 그 목적지를 연다.
{
  const { h, fire, loadedAt, startLoad, retry } = screen({ path: "/search", tab: "search" });
  loadedAt("/search");
  h.update({ target: "/search?query=brick", targetKey: "k50" });
  startLoad("/search?query=brick");
  fire("onHttpError", { nativeEvent: { url: `${O}/search?query=brick`, statusCode: 502 } });
  ok(h.web() === null, "목적지 5xx는 실패 화면");
  retry();
  eq(h.web().props.source, { uri: `${O}/search?query=brick` }, "다시 시도는 목적지를 연다");
}

// 19. 폼 보존 — 실패 뒤 온 뿌리 요청은 실패한 작성 위치를 덮지 않는다. 다시 시도는 그 위치를 연다.
{
  const { h, fire, loadedAt, retry } = screen({ path: "/sell", tab: "sell" });
  loadedAt("/sell");
  fire("onNavigationStateChange", { url: `${O}/sell?step=2`, canGoBack: true });
  fire("onError", {});
  h.update({ target: "/sell", targetKey: "k60" });
  retry();
  eq(h.web().props.source, { uri: `${O}/sell?step=2` }, "뿌리 요청 대신 실패한 /sell?step=2를 다시 연다");
}

// ── 라우터 상태 전이: 설치된 Expo Router 57의 실제 코드로 본다 ──
// 앱의 실제 라우트 파일 → getRoutes·getReactNavigationConfig·getStateFromPath(링크 설정) → getNavigateAction(router.push·navigate·
// dismissTo가 만드는 액션) → 루트 Stack(StackClient의 StackRouter = 기본 StackRouter + stackRouterOverride)·TabRouter.
// 화면을 그리는 React Native 모듈만 가짜로 꽂고, 라우팅 판단 코드는 설치된 그대로 돈다.
{
  const Module = require("node:module");
  const build = path.join(repo, "apps/mobile/node_modules/expo-router/build");
  const req = Module.createRequire(path.join(build, "index.js"));
  const stub = (rel, exports) => {
    const file = req.resolve(rel);
    require.cache[file] = { id: file, filename: file, loaded: true, exports };
  };
  const { StackRouter: BaseStackRouter } = req("./react-navigation/routers/StackRouter");
  const { TabRouter } = req("./react-navigation/routers/TabRouter");
  const CommonActions = req("./react-navigation/routers/CommonActions");
  // useScreens.js는 화면 렌더러라 그대로 불러올 수 없어, 순수 함수 getSingularId만 원문에서 떼어 쓴다.
  const useScreensSource = fs.readFileSync(path.join(build, "useScreens.js"), "utf8");
  const fnStart = useScreensSource.indexOf("function getSingularId(");
  const getSingularId = vm.runInNewContext(`(${useScreensSource.slice(fnStart, useScreensSource.indexOf("\n}\n", fnStart) + 2)})`);
  const storeStub = { navigationRef: { current: null }, linking: null, redirects: undefined, assertIsReady() {}, getRouteInfo: () => ({ segments: [], params: {} }) };
  stub("./react-navigation/native", { validatePathConfig() {}, StackRouter: BaseStackRouter });
  stub("./useScreens", { getSingularId });
  stub("./layouts/withLayoutContext", { withLayoutContext: () => () => null });
  stub("./fork/native-stack/createNativeStackNavigator", { createNativeStackNavigator: () => ({ Navigator: () => null }) });
  stub("./link/preview/LinkPreviewContext", {});
  stub("./layouts/stack-utils", { mapProtectedScreen: () => ({ children: [] }) });
  stub("./utils/children", {});
  stub("./views/Protected", {});
  stub("./global-state/store", { store: storeStub });
  stub("./getRoutesRedirects", { applyRedirects: (href) => href });
  const { getRoutes } = req("./getRoutes");
  const { getReactNavigationConfig } = req("./getReactNavigationConfig");
  const { getStateFromPath } = req("./fork/getStateFromPath");
  const { getNavigateAction } = req("./global-state/getNavigationAction");
  const { resolveHref } = req("./link/href");
  const { INTERNAL_SLOT_NAME } = req("./constants");
  const { StackRouter: AppStackRouter } = req("./layouts/StackClient");

  // 앱의 실제 라우트 파일로 링크 설정을 만든다(라우트를 추가·삭제하면 이 검사도 따라간다).
  const appDir = path.join(repo, "apps/mobile/src/app");
  const files = [];
  (function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (/\.[jt]sx?$/.test(entry.name)) files.push(`./${path.relative(appDir, full).split(path.sep).join("/")}`);
    }
  })(appDir);
  const context = Object.assign(() => ({ default: () => null }), { keys: () => files, resolve: (key) => key, id: "app" });
  const routes = getRoutes(context, { platform: "ios", skipGenerated: true, ignoreEntryPoints: true });
  const config = { screens: { [INTERNAL_SLOT_NAME]: { path: "", ...getReactNavigationConfig(routes, true) } } };
  storeStub.linking = { config, getStateFromPath: (p, c) => getStateFromPath(p, c) };
  const rootNames = Object.keys(config.screens[INTERNAL_SLOT_NAME].screens);
  const tabNames = Object.keys(config.screens[INTERNAL_SLOT_NAME].screens["(tabs)"].screens);
  ok(rootNames.includes("web") && ["index", "search", "sell", "chat", "me"].every((n) => tabNames.includes(n)), "실제 라우트에 web과 다섯 탭이 있다");

  const opts = (routeNames) => ({ routeNames, routeParamList: {}, routeGetIdList: {} });
  const tabRouter = TabRouter({});
  const stackRouter = AppStackRouter({});
  // 앱이 홈으로 뜬 뒤 사용자가 판매 탭에서 작성 중이다(검색 탭도 이미 열어 둔 상태).
  let tabs = tabRouter.getInitialState(opts(tabNames));
  tabs = tabRouter.getStateForAction(tabs, CommonActions.navigate({ name: "search", params: { query: "레고" } }), opts(tabNames));
  tabs = tabRouter.getStateForAction(tabs, CommonActions.navigate({ name: "sell", params: {} }), opts(tabNames));
  const sellKey = tabs.routes.find((r) => r.name === "sell").key;
  let stack = stackRouter.getInitialState(opts(["(tabs)", ...rootNames.filter((n) => n !== "(tabs)")]));
  stack = { ...stack, routes: stack.routes.map((r) => (r.name === "(tabs)" ? { ...r, state: tabs } : r)) };
  storeStub.navigationRef.current = {
    getRootState: () => ({ stale: false, type: "stack", key: "container", index: 0, routeNames: [INTERNAL_SLOT_NAME], routes: [{ key: "root-slot", name: INTERNAL_SLOT_NAME, state: stack }] }),
  };
  const toStack = (action) => {
    assert.equal(action.target, stack.key, "루트 Stack을 겨눈 액션");
    return stackRouter.getStateForAction(stack, action, opts(stack.routeNames));
  };
  const hrefFor = (tab, to) => resolveHref({ pathname: navigation.TAB_ROUTE[tab], params: { to, at: "req-1" } });

  // 알림을 누르면 router.push("/web?path=...")가 탭 위에 알림 상세를 쌓는다.
  stack = toStack(getNavigateAction("/web?path=%2Fnotifications", {}, "PUSH"));
  eq(stack.routes.map((r) => r.name), ["(tabs)", "web"], "알림 상세가 탭 위에 쌓인다");
  const tabsRoute = stack.routes[0];

  // 리뷰 재현: 비탭 화면에서 navigate는 아래 (tabs)로 돌아가지 않고 새 (tabs)를 쌓는다.
  const viaNavigate = toStack(getNavigateAction(hrefFor("sell", "/sell?draft=1#top"), {}, "NAVIGATE"));
  eq(viaNavigate.routes.map((r) => r.name), ["(tabs)", "web", "(tabs)"], "navigate는 [기존 tabs, web, 새 tabs]");
  ok(viaNavigate.routes[2].key !== tabsRoute.key && viaNavigate.routes[2].state === undefined, "새 (tabs)는 작성 중 탭 상태가 없다");

  // 수정: 비탭 화면은 dismissTo(POP_TO). 기존 (tabs)까지 내려가 키·중첩 상태를 두고 params만 바꾼다.
  const popTo = getNavigateAction(hrefFor("sell", "/sell?draft=1#top"), {}, "POP_TO");
  eq([popTo.type, popTo.payload.name], ["POP_TO", "(tabs)"], "dismissTo는 POP_TO (tabs)");
  stack = toStack(popTo);
  eq(stack.routes.map((r) => r.name), ["(tabs)"], "web을 닫고 중복 (tabs) 없음");
  eq([stack.index, stack.routes[0].key], [0, tabsRoute.key], "기존 (tabs) 키 유지");
  ok(stack.routes[0].state === tabsRoute.state, "중첩 탭 상태(작성 중 판매·열어 둔 검색) 그대로");
  const params = stack.routes[0].params;
  eq([params.screen, params.params.to, params.params.at], ["sell", "/sell?draft=1#top", "req-1"], "목적지 탭과 query·hash 전달");

  // 탭 내비게이터는 바뀐 params를 이렇게 소비한다(react-navigation useNavigationBuilder: params.screen → navigate).
  const nextTabs = tabRouter.getStateForAction(tabs, CommonActions.navigate({ name: params.screen, params: params.params, path: params.path, merge: params.merge, pop: params.pop }), opts(tabs.routeNames));
  const focused = nextTabs.routes[nextTabs.index];
  eq([focused.name, focused.key, focused.params.to], ["sell", sellKey, "/sell?draft=1#top"], "판매 탭은 같은 화면(키)에 목적지만 받는다");
  eq([nextTabs.routes.map((r) => r.key), nextTabs.routes.find((r) => r.name === "search").params], [tabs.routes.map((r) => r.key), { query: "레고" }], "다른 탭의 키·params도 그대로");

  // 탭 안의 이동은 기존처럼 navigate가 탭 내비게이터를 겨눈다(루트 Stack은 그대로).
  stack = { ...stack, routes: [{ ...stack.routes[0], state: nextTabs }] };
  const inTab = getNavigateAction(hrefFor("search", "/search?query=lego#list"), {}, "NAVIGATE");
  eq([inTab.target, inTab.type, inTab.payload.name, inTab.payload.params.to], [nextTabs.key, "NAVIGATE", "search", "/search?query=lego#list"], "탭 안 navigate는 탭 내비게이터로");
  const afterInTab = tabRouter.getStateForAction(nextTabs, inTab, opts(nextTabs.routeNames));
  eq([afterInTab.routes[afterInTab.index].name, afterInTab.routes[afterInTab.index].key], ["search", nextTabs.routes.find((r) => r.name === "search").key], "검색 탭도 같은 화면(키)으로");
}

console.log(`PASS: ${checks} WebScreen lifecycle cases`);
