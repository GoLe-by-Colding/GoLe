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
      useRouter: () => ({ navigate: (to) => navigated.push(to) }),
      useFocusEffect: (cb) => h.React.useEffect(() => cb(), [cb]),
    },
    "@/features/push-notifications": { useDevicePushToken: () => null, webPushTokenScript: () => "" },
    "@/shared/theme": { themes: { light: { background: "#fff", tint: "#1d4ed8", text: "#111", tabInactive: "#999", border: "#eee" } } },
    "../model/navigation": navigation,
  }, { process: { env: { EXPO_PUBLIC_WEB_BASE_URL: O } }, __DEV__: false });
  h.mount(mod.WebScreen, initialProps);
  const fire = (name, event) => h.act(() => h.web().props[name](event));
  return { h, navigated, opened, fire };
}
const assigns = (instance) => instance.injected.filter((js) => js.startsWith("window.location.assign("));

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
  const { h, fire } = screen({ path: "/search", tab: "search" });
  h.update({ target: "/search?query=%EB%A0%88%EA%B3%A0#top", targetKey: "k2" });
  eq(assigns(h.instance()), [], "로드 전에는 주입하지 않는다");
  fire("onLoad", {});
  eq(assigns(h.instance()), [`window.location.assign(${JSON.stringify(`${O}/search?query=%EB%A0%88%EA%B3%A0#top`)});true;`], "onLoad에서 query·hash 그대로 한 번 적용");
  fire("onLoad", {});
  h.update({ targetKey: "k2" });
  eq(assigns(h.instance()).length, 1, "다음 로드·같은 요청 키로는 다시 옮기지 않는다");
}

// 4. 로드 뒤에 온 목적지는 바로 옮긴다. 다른 탭의 경로·외부 원점은 무시한다.
{
  const { h, fire } = screen({ path: "/search", tab: "search" });
  fire("onLoad", {});
  h.update({ target: "/search?query=x", targetKey: "k3" });
  eq(assigns(h.instance()).length, 1, "로드 뒤 즉시 적용");
  h.update({ target: "https://evil.test/search", targetKey: "k4" });
  h.update({ target: "/chat?room=1", targetKey: "k5" });
  h.update({ target: "//evil.test/search", targetKey: "k6" });
  eq(assigns(h.instance()).length, 1, "신뢰 원점이 아니거나 다른 탭 경로면 옮기지 않는다");
}

// 5. 폼 보존 — 뿌리만 가리키는 요청은 탭이 자기 영역에 있으면 옮기지 않고, 대기열도 비운다.
{
  const { h, fire } = screen({ path: "/sell", tab: "sell" });
  fire("onLoad", {});
  fire("onNavigationStateChange", { url: `${O}/sell?step=2`, canGoBack: true });
  h.update({ target: "/sell", targetKey: "k7" });
  fire("onLoad", {});
  eq(assigns(h.instance()), [], "작성 중인 /sell?step=2를 새로 고치지 않는다");
}

// 6. 실패 화면 사이에 온 목적지도 다시 시도 → 새 WebView의 onLoad에서 적용된다.
{
  const { h, fire } = screen({ path: "/chat", tab: "chat" });
  fire("onError", {});
  ok(h.web() === null, "실패 화면으로 바뀌면 WebView가 내려간다");
  h.update({ target: "/chat?room=r9", targetKey: "k8" });
  const retry = h.find("Pressable");
  h.act(() => retry.props.onPress());
  ok(h.web() !== null && h.instances.length === 2, "다시 시도는 새 WebView");
  eq(assigns(h.instance()), [], "새 WebView도 로드 전에는 주입하지 않는다");
  fire("onLoad", {});
  eq(assigns(h.instance()), [`window.location.assign(${JSON.stringify(`${O}/chat?room=r9`)});true;`], "다시 시도 뒤 목적지 적용");
}

// 7. 웹 메시지 — 정확한 원점·고정 모양만 탭 이동으로 바뀐다.
{
  const { navigated, fire } = screen({ path: "/", tab: "home" });
  fire("onLoad", {});
  fire("onMessage", { nativeEvent: { data: JSON.stringify({ type: "gole:navigate", path: "/sell?draft=1" }), url: `${O}/` } });
  eq(navigated.length, 1, "다른 탭 요청은 그 탭으로");
  eq([navigated[0].pathname, navigated[0].params.to], ["/sell", "/sell?draft=1"], "탭 경로와 query 보존");
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
  const { navigated, fire, h } = screen({ path: "/profile", tab: "me" });
  fire("onLoad", {});
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
  const { h, fire } = screen({ path: "/search", tab: "search" });
  fire("onLoad", {});
  fire("onLoadStart", { nativeEvent: { url: `${O}/listings/abc`, loading: true } });
  h.update({ target: "/search?query=during-load", targetKey: "k9" });
  fire("onLoadProgress", { nativeEvent: { progress: 0.4 } });
  eq(assigns(h.instance()), [], "새 문서를 불러오는 중에는 주입하지 않는다");
  fire("onLoad", {});
  eq(assigns(h.instance()), [`window.location.assign(${JSON.stringify(`${O}/search?query=during-load`)});true;`], "로드가 끝나면 적용");
}

// 11. 취소된 로드 — 다른 탭으로 넘기며 막은 이동은 onLoad 없이 진행률 1로 끝난다. 그때 대기 목적지를 옮긴다.
{
  const { h, fire, navigated } = screen({ path: "/", tab: "home" });
  fire("onLoad", {});
  fire("onLoadStart", { nativeEvent: { url: `${O}/prices`, loading: true } });
  h.update({ target: "/?section=trending", targetKey: "k10" });
  eq(h.web().props.onShouldStartLoadWithRequest({ url: `${O}/chat?room=r3`, isTopFrame: true }), false, "리다이렉트로 온 다른 탭 이동을 막는다");
  eq(navigated.map((n) => n.params.to), ["/chat?room=r3"], "막은 이동은 채팅 탭으로");
  eq(assigns(h.instance()), [], "취소가 확인되기 전에는 옮기지 않는다");
  fire("onLoadProgress", { nativeEvent: { progress: 1 } });
  eq(assigns(h.instance()), [`window.location.assign(${JSON.stringify(`${O}/?section=trending`)});true;`], "진행률 1(로드 멈춤)에서 적용");
}

// 12. Android SPA 주소 변화는 onLoadStart가 loading=false로 온다 — 로드 중으로 보지 않고 목적지를 바로 옮긴다.
{
  const { h, fire } = screen({ path: "/chat", tab: "chat" });
  fire("onLoad", {});
  fire("onLoadStart", { nativeEvent: { url: `${O}/chat?room=r1`, loading: false } });
  h.update({ target: "/chat?room=r2", targetKey: "k11" });
  eq(assigns(h.instance()).length, 1, "SPA 이동 뒤에도 바로 적용");
}

console.log(`PASS: ${checks} WebScreen lifecycle cases`);
