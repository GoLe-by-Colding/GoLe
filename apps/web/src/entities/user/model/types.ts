/**
 * 인증/세션 관련 도메인 타입. 백엔드 Account 응답과 대응.
 */
export interface Session {
  readonly accountId: string;
  readonly sessionToken: string;
  readonly role: "USER" | "ADMIN";
  /** 최초 로그인 온보딩이 남았는지(onboarding R8). 소셜은 구글만 실값이고 나머지는 항상 false(D7). */
  readonly onboardingRequired: boolean;
}

export interface RegisterResult {
  readonly accountId: string;
}

/** GET /me 응답: 현재 로그인 사용자 정보. */
export interface Me {
  readonly accountId: string;
  readonly email: string;
  readonly role: "USER" | "ADMIN";
  /** 온보딩 R8. */
  readonly onboardingRequired: boolean;
}

/**
 * 온보딩 진행 상태(onboarding R2). 완료 여부는 서버가 계정 필드 유무로 파생시키며
 * 별도 플래그로 저장하지 않는다(D1) — 화면은 이 응답만 보고 남은 단계부터 재개한다.
 */
export interface OnboardingStatus {
  /** 네 단계가 모두 끝나지 않았으면 true. */
  readonly required: boolean;
  /** 스펙 배포 이전 계정(D6). true면 강제 리다이렉트 대신 배너만 노출한다. */
  readonly legacyExempt: boolean;
  readonly nicknameCompleted: boolean;
  readonly nickname: string | null;
  readonly phoneCompleted: boolean;
  readonly maskedPhoneNumber: string | null;
  readonly interestTagsCompleted: boolean;
  /** 이미 고른 태그의 key 목록. 재개 시 선택 상태를 되살리는 데 쓴다. */
  readonly interestTags: readonly string[];
  /**
   * 동의 단계에는 완료 플래그가 따로 없다. 개인정보 동의만 필수이므로
   * 이 값이 곧 단계 완료 여부이고, 마케팅 동의는 선택이라 판정에 넣지 않는다.
   */
  readonly privacyConsented: boolean;
  readonly marketingConsented: boolean;
}

/**
 * 서버가 내려주는 curated 관심 태그 한 항목(D8).
 *
 * 계정에는 `key`만 저장한다 — 표시 문구(label)를 저장하면 문구를 고칠 때마다
 * 사용자 데이터를 마이그레이션해야 한다.
 */
export interface InterestTag {
  readonly key: string;
  readonly label: string;
}

/** 관심 태그 선택 개수 제한(D8, R6). */
export const INTEREST_TAG_MIN = 1;
export const INTEREST_TAG_MAX = 5;
