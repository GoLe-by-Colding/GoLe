import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import boundaries from "eslint-plugin-boundaries";

/**
 * FSD 레이어 경계 강제.
 * 상위 → 하위로만 import 허용: app > views > widgets > features > entities > shared
 */

/** v7 의 allow 는 { to: ... } 래퍼를 요구한다. 타입 이름만 받아 그 형태로 감싼다. */
const to = (...types) => types.map((type) => ({ to: { element: { type } } }));

/** 각 레이어가 import 할 수 있는 하위 레이어 목록. */
const LAYERS_BELOW = {
  app: to("views", "widgets", "features", "entities", "shared"),
  views: to("widgets", "features", "entities", "shared"),
  widgets: to("features", "entities", "shared"),
  features: to("entities", "shared"),
  entities: to("shared"),
  shared: to("shared"),
};

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    plugins: { boundaries },
    settings: {
      "boundaries/include": ["src/**/*"],
      "boundaries/elements": [
        { type: "app", pattern: "src/app/**" },
        { type: "views", pattern: "src/views/**" },
        { type: "widgets", pattern: "src/widgets/**" },
        { type: "features", pattern: "src/features/**" },
        { type: "entities", pattern: "src/entities/**" },
        { type: "shared", pattern: "src/shared/**" },
      ],
    },
    rules: {
      // v7 문법: 규칙명 element-types → dependencies, rules → policies,
      // 타입 문자열 → 엔티티 셀렉터({ element: { type } }), allow 항목에 to 래퍼 필수.
      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          policies: [
            { from: [{ element: { type: "app" } }], allow: LAYERS_BELOW.app },
            { from: [{ element: { type: "views" } }], allow: LAYERS_BELOW.views },
            { from: [{ element: { type: "widgets" } }], allow: LAYERS_BELOW.widgets },
            { from: [{ element: { type: "features" } }], allow: LAYERS_BELOW.features },
            { from: [{ element: { type: "entities" } }], allow: LAYERS_BELOW.entities },
            { from: [{ element: { type: "shared" } }], allow: LAYERS_BELOW.shared },
          ],
        },
      ],
    },
  },
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "playwright-report/**",
    "test-results/**",
    "next-env.d.ts",
    "tests-e2e/**",
  ]),
]);

export default eslintConfig;
