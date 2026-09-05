/**
 * 코어 환경설정. 웹은 `NEXT_PUBLIC_*`에서, 앱은 `EXPO_PUBLIC_*`에서 읽어 부트스트랩에서 주입한다.
 *
 * <b>설정은 모듈 스코프가 아니라 호출 시점에 읽는다.</b> 모듈 스코프에서 읽으면 import 순서에
 * 따라 부트스트랩보다 먼저 평가될 수 있고, 그 사고는 "API 원점이 빈 문자열"이라는 조용한 형태로
 * 나타난다. 코어의 모든 요청은 함수 안에서 일어나므로 지연 읽기로 충분하다.
 */
export interface CoreConfig {
  /** 백엔드 원점. 끝의 슬래시는 없어야 한다. 운영 웹의 동일 출처는 빈 문자열이다. */
  readonly apiBaseUrl: string;
}

let config: CoreConfig | null = null;

export function configureCore(next: CoreConfig): void {
  config = Object.freeze({ ...next });
}

export function isCoreConfigured(): boolean {
  return config !== null;
}

/**
 * 미설정 상태를 기본값으로 조용히 넘기지 않는다. 빈 원점으로 요청이 나가면 엉뚱한 곳으로 가고
 * 원인이 드러나지 않는다 — 부트스트랩 누락은 즉시 실패해야 고칠 수 있다.
 */
export function requireConfig(): CoreConfig {
  if (config === null) {
    throw new Error(
      "@gole/core가 설정되지 않았습니다. 앱 시작 시 configureCore()를 먼저 호출해야 합니다.",
    );
  }
  return config;
}

/** 테스트에서 부트스트랩 상태를 되돌린다. 프로덕션 코드에서 쓰지 않는다. */
export function resetCoreConfigForTest(): void {
  config = null;
}
