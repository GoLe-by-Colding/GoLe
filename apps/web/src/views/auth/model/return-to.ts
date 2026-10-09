import type { Session } from "@entities/user";
import { isAdminPath, loginHrefWithReturnTo, resolveReturnTo } from "@shared/lib";

/**
 * 로그인 게이트의 복귀 경로 처리.
 *
 * 형태 검증(오픈 리다이렉트 방어)은 온보딩 게이트와 공유해야 해서 `shared/lib`에 있다.
 * 여기에는 역할·문구 등 인증 화면 고유의 판단만 남긴다.
 */

export { resolveReturnTo, isAdminPath, loginHrefWithReturnTo };

/**
 * 인증이 끝나 실제 권한을 알게 된 시점에 적용하는 2차 게이트.
 * 관리자 영역 복귀는 ADMIN 역할에만 허용한다.
 */
export function applyRoleGuard(
  target: string | null,
  role: Session["role"] | null | undefined,
): string | null {
  if (target === null) {
    return null;
  }
  if (isAdminPath(target) && role !== "ADMIN") {
    return null;
  }
  return target;
}

/**
 * 복귀 경로를 사람이 읽을 수 있는 짧은 이름으로 바꾼다(안내 문구용).
 * 안내 문장이 "로그인하면 {이름} 화면으로 돌아갑니다"라서 이름에는 "화면"을 붙이지 않는다
 * — 붙이면 "이전 화면 화면으로"처럼 겹친다.
 */
export function returnToLabel(target: string): string {
  const path = target.split(/[?#]/)[0] ?? target;
  if (path === "/") {
    return "홈";
  }
  if (path === "/collection") {
    return "내 컬렉션";
  }
  if (path === "/profile" || path.startsWith("/profile/")) {
    return "내 정보";
  }
  if (isAdminPath(target)) {
    return "운영자 콘솔";
  }
  // 인증 화면에 사용자가 만든 임의 경로 문자열을 브랜드 문구처럼 노출하지 않는다.
  return "이전";
}
