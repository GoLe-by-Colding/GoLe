/**
 * 온보딩 입력 규칙의 클라이언트측 사전 검증.
 *
 * 최종 판정은 언제나 서버다(닉네임 중복·번호 소유·태그 목록 유효성은 여기서 알 수 없다).
 * 여기서는 왕복 없이 즉시 돌려줄 수 있는 형식 오류만 걸러 입력 경험을 매끄럽게 한다.
 */

/** 닉네임: 2~12자, 한글/영문/숫자만(D9). */
const NICKNAME_PATTERN = /^[가-힣a-zA-Z0-9]{2,12}$/;

/** 휴대폰 번호: 하이픈을 뺀 01X 형식만 허용한다(D4). */
const PHONE_PATTERN = /^01[016789]\d{7,8}$/;

/** 형식 위반 사유를 돌려준다. 통과하면 null. */
export function validateNickname(value: string): string | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return "닉네임을 입력해 주세요.";
  }
  if (!NICKNAME_PATTERN.test(trimmed)) {
    return "닉네임은 2~12자의 한글·영문·숫자만 사용할 수 있습니다.";
  }
  return null;
}

/** 하이픈·공백을 제거해 서버로 보낼 형태로 만든다. */
export function normalizePhoneNumber(value: string): string {
  return value.replace(/[\s-]/g, "");
}

/** 형식 위반 사유를 돌려준다. 통과하면 null. */
export function validatePhoneNumber(value: string): string | null {
  const normalized = normalizePhoneNumber(value);
  if (normalized.length === 0) {
    return "휴대폰 번호를 입력해 주세요.";
  }
  if (!PHONE_PATTERN.test(normalized)) {
    return "휴대폰 번호 형식이 올바르지 않습니다. (예: 010-1234-5678)";
  }
  return null;
}
