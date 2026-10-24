# Zod 컬렉션 상한 검증과 개발 도구 호환

## 배경

탐색 UI PR #250의 CI가 2026-10-23 갱신된 `GHSA-mrhx-c7xc-m3w5`로 멈췄다. 기존 lockfile의 Zod 4.6.5와 3.25.76이 대상이다. Zod 3 배열은 `.max(1)`에 1,000개를 주어도 모든 원소를 검증하는 것으로 재현했다. 검사 예외를 추가하지 않고 실제 처리량 경계와 기존 도구 동작을 함께 고친다.

## 수용 기준

1. 의존성 그래프가 수정된 Zod 4 버전을 사용하고, `pnpm audit --audit-level high`가 기존 예외 목록 변경 없이 통과한다.
2. Zod 3 API를 쓰는 Expo CLI·Steiger·오류 포매터는 명시적으로 `zod/v3`를 사용한다. 함수 스키마와 부분 enum record 동작을 유지한다.
3. 호환 엔트리의 배열 `.max()`/`.length()`, 집합 `.max()`/`.size()`는 상한 초과를 원소 검증 전에 거부한다. ESM/CJS와 동기/비동기 모두 적용한다.
4. 상한 이하·무제한·최솟값·원소 오류의 검증 결과를 보존한다. 안전한 크기의 입력과 원소 방문 횟수로 회귀를 증명한다.
5. 패치는 lockfile 해시로 고정하고 실제 소비 모듈 및 웹·모바일 품질 게이트로 호환성을 확인한다. 사용자 프리뷰와 결제 잠금을 유지한다.

## 근거

- [공식 보안 공지](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5)
- [Zod 4 수정](https://github.com/colinhacks/zod/pull/6652)
- [Zod 3 지원 상태와 호환 엔트리 논의](https://github.com/colinhacks/zod/issues/6653)
