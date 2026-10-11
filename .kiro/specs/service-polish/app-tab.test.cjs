// 앱 탭 ↔ 웹 목적지 동기화 회귀 검사. 실행: node .kiro/specs/service-polish/app-tab.test.cjs
// 앱(apps/mobile views/web/model/navigation.ts)과 웹(apps/web shared/lib/app-tab-link.ts)을 그대로 불러
// 탭 표 일치, 메시지 검증(원점·모양·경로), 왕복, 폼 보존 규칙을 확인한다.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("../../../apps/mobile/node_modules/typescript");
const repo = path.resolve(__dirname, "../../..");
function load(relative, mocks = {}, globals = {}) {
  const exports = {};
  const source = ts.transpileModule(fs.readFileSync(path.join(repo, relative), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  vm.runInNewContext(source, { exports, URL, JSON, ...globals, require: (name) => {
    if (!(name in mocks)) throw new Error(`Unexpected import: ${name}`);
    return mocks[name];
  }});
  return exports;
}
const shared = load("apps/mobile/src/shared/lib/notification-route.ts");
const app = load("apps/mobile/src/views/web/model/navigation.ts", { "@/shared/lib": shared });
const web = load("apps/web/src/shared/lib/app-tab-link.ts");
const O = "https://gole.co.kr";
let checks = 0;
const same = (actual, expected, label) => { assert.deepEqual(actual === null ? null : { ...actual }, expected, label); checks++; };

// 1. 탭 표 — 앱과 웹이 같은 경로를 같은 탭으로 본다.
for (const [pathname, tab] of [
  ["/", "home"], ["/search", "search"], ["/search/x", "search"], ["/searchx", null],
  ["/sell", "sell"], ["/chat", "chat"], ["/chat-room", null], ["/profile", "me"],
  ["/profile/security", "me"], ["/listings/abc", null], ["/prices", null], ["/me", null],
]) {
  assert.equal(app.tabForPath(pathname), tab, `app ${pathname}`);
  assert.equal(web.appTabForPath(pathname), tab, `web ${pathname}`); checks++;
}

// 2. 탭 요청 — 신뢰 원점의 탭 경로만, query·hash 보존.
same(app.tabRequest("/chat?room=r1#m", O), { tab: "chat", path: "/chat?room=r1#m" });
same(app.tabRequest(`${O}/search?query=%EB%B8%8C%EB%A6%AD`, O), { tab: "search", path: "/search?query=%EB%B8%8C%EB%A6%AD" });
for (const raw of ["https://evil.test/search", "https://gole.co.kr.evil.test/search", "//evil.test/search",
  "https://user:pw@gole.co.kr/search", "/listings/abc", "http://gole.co.kr/search"]) {
  same(app.tabRequest(raw, O), null, raw);
}

// 3. 웹 메시지 검증 — 정확한 원점·두 키·고정 종류·안전한 상대 경로만.
const msg = (body) => JSON.stringify(body);
same(app.appNavigationMessage(msg({ type: "gole:navigate", path: "/sell" }), `${O}/listings/abc`, O), { tab: "sell", path: "/sell" });
same(app.appNavigationMessage(msg({ path: "/profile/security", type: "gole:navigate" }), `${O}/`, O), { tab: "me", path: "/profile/security" });
for (const [data, source, label] of [
  [msg({ type: "gole:navigate", path: "/sell" }), "https://evil.test/x", "다른 원점"],
  [msg({ type: "gole:navigate", path: "/sell" }), "https://gole.co.kr.evil.test/", "원점 접두 속임"],
  [msg({ type: "gole:navigate", path: "/sell" }), "about:blank", "about:blank"],
  [{ type: "gole:navigate", path: "/sell" }, `${O}/`, "문자열 아님"],
  ["{not json", `${O}/`, "깨진 JSON"],
  [msg(["gole:navigate", "/sell"]), `${O}/`, "배열"],
  [msg({ type: "gole:navigate", path: "/sell", script: "alert(1)" }), `${O}/`, "추가 키"],
  [msg({ type: "gole:eval", path: "/sell" }), `${O}/`, "다른 종류"],
  [msg({ type: "gole:navigate", path: "https://evil.test/search" }), `${O}/`, "절대 URL"],
  [msg({ type: "gole:navigate", path: "//evil.test/search" }), `${O}/`, "프로토콜 상대"],
  [msg({ type: "gole:navigate", path: "/%2f%2fevil.test" }), `${O}/`, "인코딩 우회"],
  [msg({ type: "gole:navigate", path: "/search\\evil" }), `${O}/`, "백슬래시"],
  [msg({ type: "gole:navigate", path: "/chat?room=%0a" }), `${O}/`, "제어 문자"],
  [msg({ type: "gole:navigate", path: "/listings/abc" }), `${O}/`, "탭 밖 경로"],
  [msg({ type: "gole:navigate", path: "/" + "a".repeat(2048) }), `${O}/`, "긴 경로"],
  ["x".repeat(4097), `${O}/`, "긴 메시지"],
]) {
  same(app.appNavigationMessage(data, source, O), null, label);
}

// 4. 왕복 — 웹이 만든 메시지를 앱이 그대로 받아들인다.
const round = (href, page, tab) => {
  const m = web.appTabNavigation(href, page, tab);
  return m === null ? null : app.appNavigationMessage(m, page, O);
};
same(round("/search?query=x#a", `${O}/`, "home"), { tab: "search", path: "/search?query=x#a" });
same(round("../sell", `${O}/listings/abc`, "home"), { tab: "sell", path: "/sell" });
same(round("/chat", `${O}/notifications`, null), { tab: "chat", path: "/chat" }, "탭이 아닌 화면");
for (const [href, page, tab, label] of [
  ["/search", `${O}/search`, "search", "같은 탭 안 이동은 웹이 연다"],
  ["https://evil.test/search", `${O}/`, "home", "외부 원점"],
  ["/listings/abc", `${O}/`, "home", "탭 밖 경로"],
  ["mailto:help@gole.co.kr", `${O}/`, "home", "mailto"],
]) {
  assert.equal(web.appTabNavigation(href, page, tab), null, label); checks++;
}

// 5. 탭 표시 주입 — 앱이 넣은 JSON 상수를 웹이 읽는다. 앱 밖이면 undefined, 모르는 값은 null.
for (const [tab, expected] of [["chat", "chat"], [null, null]]) {
  const scope = {};
  vm.runInNewContext(app.appTabScript(tab), { window: scope });
  assert.equal(load("apps/web/src/shared/lib/app-tab-link.ts", {}, { window: scope }).readAppTab(), expected); checks++;
}
assert.equal(load("apps/web/src/shared/lib/app-tab-link.ts", {}, { window: { __GOLE_APP_TAB__: "admin" } }).readAppTab(), null); checks++;
assert.equal(web.readAppTab(), undefined, "앱 밖(window 없음)"); checks++;

// 6. 폼 보존 — 뿌리만 가리키는 요청은 그 탭이 자기 영역에 있으면 옮기지 않는다.
for (const [target, current, tab, expected] of [
  ["/sell", "/sell?step=2", "sell", false],
  ["/sell", "/listings/abc", "sell", true],
  ["/search?query=x", "/search", "search", true],
  ["/search?query=x", "/search?query=x", "search", false],
  ["/chat?room=r2", "/chat?room=r1", "chat", true],
  ["/profile", "/profile/security", "me", false],
  ["/", "/?ref=home", "home", false],
]) {
  assert.equal(app.shouldMoveTab(target, current, tab), expected, `${target} ← ${current}`); checks++;
}

// 7. 탭 경로 — expo-router 탭 이름과 웹 뿌리가 어긋나지 않는다(웹 /profile ↔ 앱 /me).
assert.deepEqual({ ...app.TAB_ROUTE }, { home: "/", search: "/search", sell: "/sell", chat: "/chat", me: "/me" }); checks++;
assert.deepEqual({ ...app.TAB_PATH }, { home: "/", search: "/search", sell: "/sell", chat: "/chat", me: "/profile" }); checks++;
for (const [tab, root] of Object.entries(app.TAB_PATH)) {
  assert.equal(web.appTabForPath(root), tab, `웹 표와 앱 뿌리 ${root}`); checks++;
}
for (const name of ["index", "search", "sell", "chat", "me"]) {
  const screen = fs.readFileSync(path.join(repo, `apps/mobile/src/app/(tabs)/${name}.tsx`), "utf8");
  const tab = name === "index" ? "home" : name;
  assert.match(screen, new RegExp(`tab="${tab}" target=\\{to\\} targetKey=\\{at\\}`), name); checks++;
}

console.log(`PASS: ${checks} app tab sync cases`);
