# 검증 근거

2026-10-24, 탐색 UI PR #250의 보안 선행 수정.

## 실제 수정 범위

Zod를 4.6.7로 고정하고 기본 v4 엔트리는 공식 보안 수정을 사용한다. 포함된 v3 호환 엔트리는 여전히 상한 초과 후 원소를 순회하므로 배열 `.max()`/`.length()`와 집합 `.max()`/`.size()`에 실제 조기 종료를 추가했다. v3의 Map은 크기 제한 API가 없어 새 동작을 만들지 않았다. 무제한 컬렉션은 사용자가 명시한 상한이 없으므로 기존 동작을 유지한다.

| 패키지 | 패치 | lockfile SHA-256 |
| --- | --- | --- |
| zod 4.6.7 | v3 소스·ESM·CJS 상한 조기 종료 | `608c3939121a218c1aaddcbfa4842e98b7cdf7fce08b6f11fd208c33f8844a3a` |
| steiger 0.7.0 | 런타임·타입·소스 import를 `zod/v3`로 한정 | `3e0628d504a4ad09bfa545501066b50961737b13be76ddb9ac8d9c32a79d39b4` |
| zod-validation-error 3.5.4 | ESM·CJS·타입 import를 `zod/v3`로 한정 | `ff2ab1c57615d49543d98f2100cde3db33c77faa4cb0a7b32b0af95866fdf0f7` |
| @expo/cli 57.0.28 | 개발 도구 스키마 두 곳을 `zod/v3`로 한정 | `4b2d966465b4609aa6b38332077df669f95f803c9dbd0ec980ecfd3e0a5d7ea3` |

## 로컬 결과

- `pnpm install --ignore-scripts --frozen-lockfile`: 통과. 버전·패치 해시 일치 확인.
- `pnpm audit --audit-level high`: exit 0. low 2·moderate 4·high 1(기존 예외 1)이며 **취약점 전체가 0이라는 뜻은 아니다**. `auditConfig.ignoreGhsas`는 변경하지 않았다.
- `zod-compatibility.spec.ts`: **10 통과, 실패 0, 스킵 0**, 재시도 없이 실행. 실제 Steiger/Expo 의존성을 해석해 검사하며 별도 테스트용 Zod는 설치하지 않는다.
- 상한 0·1과 안전한 1,000개 입력을 사용해 v3/기본 v4 × ESM/CJS × 동기/비동기의 원소 방문 및 후속 refinement가 0임을 확인했다. 정상 변환·무제한·하한·원소 오류와 Steiger 부분 enum 설정/함수 반환/오류 포매터, Expo 명령 스키마의 거부 계약도 확인했다.
- 처음 회귀 작성 시 v4 집합의 오류 경로를 v3처럼 `[0]`으로 가정한 2건이 실패했다. 실제 v4는 인덱스 없는 집합 경로 `[]`를 사용하므로 버전별 기존 계약으로 바로잡았다.
- 웹 `format:check → lint → typecheck → fsd:lint → build`: 통과. 별도 `.next/collector-build`와 결제 비활성으로 빌드했고 자동 추가된 임시 tsconfig 항목은 제거했다.
- core 포맷·타입·플랫폼 경계, mobile 포맷·lint·타입: 통과. 단발 iOS export 1,280모듈 성공, 결과 `/tmp/gole-zod-ios-export`.
- 기존 사용자 프리뷰 `http://localhost:3000/search?q=lego` HTTP 200. WEB 3000/API 8090/Metro 8082는 시작·종료하지 않았다.
- 별도 `codex review`: 이 보안 변경에 실행 가능한 지적 없음. 소비자 경로·패치 해시·10개 계약 콜백을 확인했다. 실제 audit·전체 품질 게이트는 위 로컬 실행 결과로 구분한다.

## 한계와 후속 제거 조건

실기기 네이티브 실행·운영 결제·외부 메시지는 검증하지 않았다. Expo 플러그인은 스키마만 검증했으며 실제 명령은 실행하지 않았다. 상한이 없는 스키마의 대량 입력 전체를 방어한다고 주장하지 않는다.

upstream 도구가 Zod 4 API로 이전하면 소비자 import 패치를 제거하고, v3 호환 엔트리 수정이 배포되면 상한 패치를 제거한다. 업데이트 시 이 계약 테스트와 frozen install을 유지해 누락된 패치를 검출한다. 최종 CI·dev 병합 결과는 PR #250과 볼트 프론트 개발일지에서 확인한다.
