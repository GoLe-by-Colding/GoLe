import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
  {
    // insignificant-slice는 "참조가 적은 슬라이스"를 지적하는 휴리스틱.
    // 스캐폴드 초기(빈 placeholder, 단일 참조)에는 노이즈라 비활성.
    // 핵심 구조/의존 규칙(forbidden-imports, public-api 등)은 유지된다.
    rules: {
      "fsd/insignificant-slice": "off",
    },
  },
  {
    // Next.js App Router는 src/app을 라우팅에 사용하므로 FSD app 레이어 규칙 완화
    files: ["./src/app/**"],
    rules: {
      "fsd/no-segmentless-slices": "off",
      "fsd/no-public-api-sidestep": "off",
    },
  },
  {
    // 엔티티는 @gole/core 위의 파사드다. 모델·API가 웹·앱 공유 패키지로 옮겨가면서
    // 대부분의 슬라이스에 index.ts만 남았다 — 세그먼트가 없는 것이 아니라 다른 패키지에 있다.
    // 상위 75개 파일의 import를 그대로 두기 위해 파사드를 택했고, 그 대가로 이 규칙을 끈다.
    // 구조 규칙(forbidden-imports, public-api 등)은 그대로 유지된다.
    files: ["./src/entities/**"],
    rules: {
      "fsd/no-segmentless-slices": "off",
    },
  },
]);
