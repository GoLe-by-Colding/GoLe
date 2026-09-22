const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { createRequire } = require('node:module');
const { resolve } = require('node:path');
const vm = require('node:vm');
const mobileRequire = createRequire(resolve('apps/mobile/package.json'));
const ts = mobileRequire('typescript');
const source = readFileSync('apps/mobile/src/features/push-notifications/lib/device-push-token.ts', 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;

function load({ os = 'ios', device = true, expoGo = false, granted = true, permissionError = false, nativeError = false } = {}) {
  const calls = [];
  const exports = {};
  const modules = {
    'expo-device': { isDevice: device },
    'expo-constants': { __esModule: true, default: { executionEnvironment: expoGo ? 'storeClient' : 'bare' }, ExecutionEnvironment: { StoreClient: 'storeClient' } },
    'react-native': { Platform: { OS: os } },
    'expo-notifications': {
      getPermissionsAsync: async () => { if (permissionError) throw Error('permission'); return { granted, canAskAgain: false }; },
      getDevicePushTokenAsync: async () => { calls.push('expo-native'); return { data: 'android-fcm' }; },
    },
    '@react-native-firebase/messaging': {
      getMessaging: () => ({ isDeviceRegisteredForRemoteMessages: false }),
      registerDeviceForRemoteMessages: async () => calls.push('register-ios'),
      getToken: async () => { calls.push('firebase-token'); return 'ios-fcm'; },
    },
  };
  vm.runInNewContext(code, { exports, require: name => {
    if (name === '@react-native-firebase/messaging') { calls.push('import-firebase'); if (nativeError) throw Error('native unavailable'); }
    assert.ok(name in modules, name);
    return modules[name];
  } });
  return { get: exports.getDevicePushToken, calls };
}

test('iOS uses Firebase registration token, never Expo APNs token', async () => {
  const s = load(); const result = await s.get();
  assert.equal(result.token, 'ios-fcm'); assert.equal(result.platform, 'IOS');
  assert.deepEqual(s.calls, ['import-firebase', 'register-ios', 'firebase-token']);
});
test('Android keeps native FCM path', async () => {
  const s = load({ os: 'android' }); const result = await s.get();
  assert.equal(result.token, 'android-fcm'); assert.equal(result.platform, 'ANDROID');
  assert.deepEqual(s.calls, ['expo-native']);
});
for (const options of [{ expoGo: true }, { device: false }, { os: 'web' }, { granted: false }, { permissionError: true }]) {
  test(`safe no-registration ${JSON.stringify(options)}`, async () => {
    const s = load(options); assert.equal(await s.get(), null); assert.deepEqual(s.calls, []);
  });
}
test('missing Firebase native module does not crash app', async () => {
  assert.equal(await load({ nativeError: true }).get(), null);
});

test('notification routes map server paths and reject external encodings', () => {
  const routeSource = readFileSync('apps/mobile/src/shared/lib/notification-route.ts', 'utf8');
  const routeCode = ts.transpileModule(routeSource, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(routeCode, { exports });
  const route = exports.notificationRoute;
  assert.equal(route('/listings/abc-123'), '/listing/abc-123');
  assert.equal(route('/sets/21318'), '/set/21318');
  assert.equal(route('/seller/person_1'), '/seller/person_1');
  assert.equal(route('/notifications'), '/notifications');
  assert.equal(route('/community/post-1'), '/notifications');
  for (const input of ['https://evil.test', '//evil.test', '/%2fevil.test', '/%252fevil.test', '/\\evil.test', '/a/../b', '/a%0ab', '/a?redirect=https://evil.test', '/%', null]) {
    assert.equal(route(input), null, String(input));
  }
});
