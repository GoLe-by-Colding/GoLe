/**
 * 인증/세션 관련 도메인 타입. 백엔드 Account 응답과 대응.
 */
export interface Session {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: "USER" | "ADMIN";
  /** 브라우저가 서버에 토큰 회전을 요청할 다음 시각(epoch ms). API 응답에는 없고 로컬 메타데이터에만 쓴다. */
  readonly refreshAfter?: number;
}

export interface RegisterResult {
  readonly accountId: string;
}

export interface CurrentSignupPolicy {
  readonly termsVersion: string;
  readonly privacyVersion: string;
  readonly minimumAge: number;
}

/** 가입 요청에 함께 보내 서버가 버전·확인 여부를 다시 검증하는 값. */
export interface SignupPolicyAcceptance {
  readonly termsVersion: string;
  readonly privacyVersion: string;
  readonly termsAccepted: boolean;
  readonly privacyAcknowledged: boolean;
  readonly minimumAgeConfirmed: boolean;
}

/** GET /me 응답: 현재 로그인 사용자 정보. */
export interface Me {
  readonly accountId: string;
  readonly email: string;
  readonly role: "USER" | "ADMIN";
}
