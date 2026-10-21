const assert = require("node:assert/strict");
const fs = require("node:fs");
const test = require("node:test");
const vm = require("node:vm");
const ts = require("typescript");

const source = ts.transpileModule(
  fs.readFileSync(require.resolve("./device-push-token.ts"), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS } },
).outputText;

function device({
  granted = true,
  isDevice = true,
  environment = "standalone",
  channelFails = false,
} = {}) {
  const calls = [];
  let channelReady = false;
  const mocks = {
    "expo-device": { isDevice },
    "expo-constants": {
      __esModule: true,
      default: { executionEnvironment: environment },
      ExecutionEnvironment: { StoreClient: "storeClient" },
    },
    "react-native": { Platform: { OS: "android" } },
    "expo-notifications": {
      AndroidImportance: { DEFAULT: 5 },
      async setNotificationChannelAsync() {
        calls.push("channel");
        if (channelFails) throw new Error("channel unavailable");
        channelReady = true;
      },
      async getPermissionsAsync() {
        calls.push("permissions");
        return { granted: false, canAskAgain: true };
      },
      async requestPermissionsAsync() {
        calls.push("request");
        // Android 13의 채널 생성 전 권한 프롬프트 실패를 재현한다.
        return { granted: channelReady && granted };
      },
      async getDevicePushTokenAsync() {
        calls.push("token");
        return { data: "fake-android-device-token" };
      },
    },
  };
  const exports = {};
  vm.runInNewContext(source, {
    exports,
    require(name) {
      assert.ok(name in mocks, `예상하지 않은 모듈: ${name}`);
      return mocks[name];
    },
  });
  return { calls, getToken: exports.getDevicePushToken };
}

test("Android는 채널 생성 뒤 권한을 요청하고 허용된 단말 토큰을 반환한다", async () => {
  const { calls, getToken } = device();
  const token = await getToken();
  assert.equal(token?.platform, "ANDROID");
  assert.equal(token?.token, "fake-android-device-token");
  assert.deepEqual(calls, ["channel", "permissions", "request", "token"]);
});

test("권한 거절 시 단말 토큰 API를 호출하지 않는다", async () => {
  const { calls, getToken } = device({ granted: false });
  assert.equal(await getToken(), null);
  assert.deepEqual(calls, ["channel", "permissions", "request"]);
});

test("채널 생성 실패는 앱을 중단하지 않고 등록을 생략한다", async () => {
  const { calls, getToken } = device({ channelFails: true });
  assert.equal(await getToken(), null);
  assert.deepEqual(calls, ["channel"]);
});

test("시뮬레이터와 Expo Go는 권한·토큰 API를 호출하지 않는다", async () => {
  for (const options of [{ isDevice: false }, { environment: "storeClient" }]) {
    const { calls, getToken } = device(options);
    assert.equal(await getToken(), null);
    assert.deepEqual(calls, []);
  }
});
