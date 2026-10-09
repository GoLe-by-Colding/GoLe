const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("../../../apps/mobile/node_modules/typescript");
const root = path.resolve(__dirname, "../../../apps/mobile/src");
function load(relative, mocks = {}) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(root, relative), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, { exports, require: (name) => {
    if (!(name in mocks)) throw new Error(`Unexpected import: ${name}`);
    return mocks[name];
  }});
  return exports;
}
const routes = load("shared/lib/notification-route.ts");
let checks = 0;
for (const [input, expected] of [
  ["/listings/abc-12", "/listing/abc-12"],
  ["/sets/75192-1", "/set/75192-1"],
  ["/sellers/u_1", "/seller/u_1"],
  ["/chat", "/chat"],
  ["/", "/"],
]) {
  assert.equal(routes.notificationRoute(input), expected); checks++;
}
for (const [input, expected] of [
  ["/chat?room=room-123", "/chat?room=room-123"],
  ["/orders/order-123#review", "/orders/order-123#review"],
  ["/parts/part-12", "/parts/part-12"],
  ["/community/post-123", "/community/post-123"],
  ["/shops/u_1", "/shops/u_1"],
  ["/seller/u_1?tab=reviews", "/shops/u_1?tab=reviews"],
  ["/listing/item-1#photos", "/listings/item-1#photos"],
  ["/search?q=%EB%B8%8C%EB%A6%AD%20%ED%8F%AC%EC%9E%A5", "/search?q=%EB%B8%8C%EB%A6%AD%20%ED%8F%AC%EC%9E%A5"],
]) {
  const route = routes.notificationRoute(input);
  assert.equal(new URL(route, "https://app.test").searchParams.get("path"), expected);
  assert.equal(routes.notificationWebPath(expected), expected); checks++;
}
for (const input of [null, {}, 1, "https://evil.test", "//evil.test", "/%2fevil.test", "/%252fevil.test", "/chat\\evil", "/chat%5cevil", "/chat?room=%00", "/chat?room=%0a", "/chat?room=bad\n", "/chat?room=%", "/orders/../admin", "/orders/%2e%2e/admin", "/api/v1/accounts", "/admin", "/unknown", "/" + "a".repeat(2048)]) {
  assert.equal(routes.notificationRoute(input), null, String(input)); checks++;
}
// 루트 준비 이전, 콜드스타트, 중복 delivery, iOS/Android payload, 구독 해제를 검증한다.
let effects = [], handler, removed = 0, cleared = 0, initial = null, ready = false;
const ref = { current: null }, pushes = [];
const defaultAction = "default";
const response = (id, link, kind = "content", action = defaultAction) => ({
  actionIdentifier: action,
  notification: { date: 1234, request: {
    identifier: id,
    content: { data: kind === "content" ? { link } : {} },
    trigger: kind === "ios" ? { payload: { link } } : kind === "android" ? { remoteMessage: { data: { link } } } : null,
  }},
});
const { usePushRegistration } = load("features/push-notifications/model/use-push-registration.ts", {
  react: { useEffect: (effect) => effects.push(effect), useRef: () => ref },
  "expo-router": { useRouter: () => ({ push: (route) => pushes.push(route) }), useRootNavigationState: () => ready ? { key: "root" } : undefined },
  "expo-notifications": {
    DEFAULT_ACTION_IDENTIFIER: defaultAction,
    addNotificationResponseReceivedListener: (fn) => { handler = fn; return { remove: () => removed++ }; },
    getLastNotificationResponse: () => initial,
    clearLastNotificationResponse: () => { initial = null; cleared++; },
  },
  "@gole/core/notification": { registerDeviceToken: () => { throw new Error("Web owns auth"); } },
  "../lib/device-push-token": {},
  "@/shared/lib": routes,
});
function render() { effects = []; usePushRegistration(false); return effects.map((effect) => effect()); }
initial = response("cold", "/orders/o1#review");
render(); assert.equal(handler, undefined); assert.equal(pushes.length, 0); checks++;
ready = true;
const cleanup = render().filter(Boolean);
assert.equal(pushes.length, 1); assert.equal(initial, null); checks++;
handler(response("cold", "/orders/o1#review")); assert.equal(pushes.length, 1); checks++;
handler(response("ios", "/chat?room=r1", "ios"));
handler(response("android", "/parts/p1", "android"));
assert.equal(pushes.length, 3); checks++;
handler(response("bad", "https://evil.test"));
handler(response("other-action", "/chat", "content", "dismiss"));
assert.equal(pushes.length, 3); assert.equal(cleared, 4); checks++;
cleanup.forEach((fn) => fn()); assert.equal(removed, 1); checks++;
render(); assert.equal(pushes.length, 3); checks++;
console.log(`PASS: ${checks} notification route/lifecycle cases`);
