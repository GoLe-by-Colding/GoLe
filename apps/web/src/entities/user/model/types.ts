/**
 * 인증/세션 관련 도메인 타입. 백엔드 Account 응답과 대응.
 */
export interface Session {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: "USER" | "ADMIN";
}

export interface RegisterResult {
  readonly accountId: string;
}

/** GET /me 응답: 현재 로그인 사용자 정보. */
export interface Me {
  readonly accountId: string;
  readonly email: string;
  readonly role: "USER" | "ADMIN";
}
