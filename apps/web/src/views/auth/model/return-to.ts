import type { Session } from "@entities/user";

/**
 * 로그인 게이트가 전달한 복귀 경로(`?returnTo=`)를 검증한다.
 *
 * 값은 사용자가 조작할 수 있는 입력이므로 **같은 오리진의 상대 경로**만 허용한다.
 * 절대 URL(`https://evil.test`), 프로토콜 상대 경로(`//evil.test`),
 * 스킴 주입(`javascript:`), 백슬래시 우회(`/\evil.test`)는 모두 거부하고 안전한 기본값으로 떨어진다.
 */

/** 오리진 판정용 기준값. 실제 네트워크와 무관한 파싱 전용 base다. */
const PARSE_BASE = "http://return-to.invalid";

/** 인증 흐름 자체로 되돌아가 루프가 생기는 경로는 복귀 대상에서 제외한다. */
const DENIED_PREFIXES: readonly string[] = ["/login", "/signup", "/verify", "/auth"];

/** 복귀 경로 최대 길이. 비정상적으로 긴 입력은 파싱 전에 거부한다. */
const MAX_LENGTH = 512;

/** 제어문자·공백은 우회 시도로 보고 거부한다. */
const UNSAFE_CHARS = /[\s\u0000-\u001f\u007f]/;

/** 관리자 전용 영역인지 판정한다. */
export function isAdminPath(path: string): boolean {
  return path === "/admin" || path.startsWith("/admin/") || path.startsWith("/admin?");
}

function isDenied(target: string): boolean {
  return DENIED_PREFIXES.some(
    (prefix) =>
      target === prefix || target.startsWith(`${prefix}/`) || target.startsWith(`${prefix}?`),
  );
}

/**
 * 형태 검증만 수행한다(권한 검증은 {@link applyRoleGuard}가 담당).
 * 통과하면 정규화된 `pathname + search + hash`를, 아니면 `null`을 돌려준다.
 */
export function resolveReturnTo(raw: string | null | undefined): string | null {
  if (typeof raw !== "string") {
    return null;
  }
  const value = raw.trim();
  if (value.length === 0 || value.length > MAX_LENGTH) {
    return null;
  }
  // 상대 경로만 허용한다. `javascript:`·`https:`·`foo/bar`는 여기서 걸린다.
  if (!value.startsWith("/")) {
    return null;
  }
  if (UNSAFE_CHARS.test(value)) {
    return null;
  }

  let parsed: URL;
  try {
    parsed = new URL(value, PARSE_BASE);
  } catch {
    return null;
  }
  // `//evil.test`, `/\evil.test`는 파싱 단계에서 오리진이 바뀌므로 여기서 걸린다.
  if (parsed.origin !== PARSE_BASE) {
    return null;
  }

  const target = `${parsed.pathname}${parsed.search}${parsed.hash}`;
  if (!target.startsWith("/") || target.startsWith("//")) {
    return null;
  }
  if (isDenied(target)) {
    return null;
  }
  return target;
}

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

/** 검증을 통과한 복귀 경로를 담은 로그인 링크를 만든다. */
export function loginHrefWithReturnTo(target: string): string {
  const safe = resolveReturnTo(target);
  return safe === null ? "/login" : `/login?returnTo=${encodeURIComponent(safe)}`;
}

/** 복귀 경로를 사람이 읽을 수 있는 짧은 이름으로 바꾼다(안내 문구용). */
export function returnToLabel(target: string): string {
  const path = target.split("?")[0] ?? target;
  if (path === "/") {
    return "홈";
  }
  if (path === "/collection") {
    return "내 컬렉션";
  }
  if (isAdminPath(target)) {
    return "운영자 콘솔";
  }
  // 인증 화면에 사용자가 만든 임의 경로 문자열을 브랜드 문구처럼 노출하지 않는다.
  return "이전 화면";
}
