/**
 * 세션 토큰 보관 추상. 코어는 `window`도 SecureStore도 알지 않는다.
 *
 * <b>동기 인터페이스인 이유</b>: 요청마다 헤더를 만드는 경로가 동기다. 앱의 SecureStore는
 * 비동기이므로 <b>부트스트랩에서 1회 읽어 메모리에 캐시</b>하고 로그인·로그아웃 때 함께 갱신한다.
 * 인터페이스를 비동기로 바꾸면 apiRequest와 그 호출부 전부가 전염되므로, 그 비용은 코어가 아니라
 * 플랫폼 어댑터가 진다.
 */
export interface SessionStore {
  /** 인증 헤더. 세션이 없으면 빈 객체. */
  readAuthorizationHeader(): Readonly<Record<string, string>>;
  /** 서버가 세션 무효를 확정했을 때 보관 중인 세션을 폐기한다. */
  clear(): void;
}

/**
 * 기본값은 no-op이다. 웹의 서버 컴포넌트처럼 세션 저장소가 없는 실행 맥락이 실제로 존재하고,
 * 그 경로는 호출부가 헤더를 직접 넘긴다. 설정(configureCore)과 달리 던지지 않는 이유다.
 */
const NO_SESSION: SessionStore = {
  readAuthorizationHeader: () => ({}),
  clear: () => undefined,
};

let store: SessionStore = NO_SESSION;

export function setSessionStore(next: SessionStore): void {
  store = next;
}

export function getSessionStore(): SessionStore {
  return store;
}

/** 테스트에서 주입을 되돌린다. */
export function resetSessionStoreForTest(): void {
  store = NO_SESSION;
}
