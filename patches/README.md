# 개발 도구 Zod 호환 패치

`GHSA-mrhx-c7xc-m3w5` 대응으로 Zod 기본 엔트리는 4.6.7, 기존 v3 소비자는 `zod/v3`로 고정한다. v3 호환 엔트리의 컬렉션 상한 검증도 실제로 보강했으므로 import 경로만 바꾸거나 해당 패치를 빼서는 안 된다. 감사 예외는 추가하지 않았다.

설계·정확한 패치 해시·제거 조건은 [스펙](../.kiro/specs/zod-collection-security/design.md)과 [검증 근거](../.kiro/specs/zod-collection-security/verification.md)를 따른다. 의존성을 갱신할 때 frozen install, audit, `apps/web/tests-e2e/zod-compatibility.spec.ts`, 웹 FSD와 모바일 export를 함께 검증한다.
