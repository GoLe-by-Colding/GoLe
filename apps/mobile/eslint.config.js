// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require("eslint-config-expo/flat");

module.exports = defineConfig([
  expoConfig,
  {
    // .expo/ 는 expo start 가 만드는 생성물이고 gitignore 대상이다.
    // 여기 경고를 띄우면 라우트를 추가할 때마다 없어졌다 생겼다 한다.
    ignores: ["dist/*", ".expo/*"],
  }
]);
