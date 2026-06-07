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
]);
