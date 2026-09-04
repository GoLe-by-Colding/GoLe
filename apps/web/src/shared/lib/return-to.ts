/**
 * 게이트가 전달한 복귀 경로(`?returnTo=`)를 검증한다.
 *
 * 값은 사용자가 조작할 수 있는 입력이므로 **같은 오리진의 상대 경로**만 허용한다.
 * 절대 URL(`https://evil.test`), 프로토콜 상대 경로(`//evil.test`),
 * 스킴 주입(`javascript:`), 백슬래시 우회(`/\evil.test`)는 모두 거부하고 안전한 기본값으로 떨어진다.
 *
 * 로그인 게이트(views/auth)와 온보딩 게이트(views/onboarding) 양쪽이 같은 판정을 써야 해서
 * shared에 둔다 — 오픈 리다이렉트 방어를 슬라이스마다 복제하면 한쪽만 고쳐지는 사고가 난다.
 * 역할 기반 2차 게이트(applyRoleGuard)는 entities를 알아야 하므로 views에 남는다.
 */

/** 오리진 판정용 기준값. 실제 네트워크와 무관한 파싱 전용 base다. */
const PARSE_BASE = "http://return-to.invalid";

/** 인증·온보딩 흐름 자체로 되돌아가 루프가 생기는 경로는 복귀 대상에서 제외한다. */
const DENIED_PREFIXES: readonly string[] = ["/login", "/signup", "/verify", "/auth", "/onboarding"];

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
 * 형태 검증만 수행한다(권한 검증은 views의 applyRoleGuard가 담당).
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

/** 검증을 통과한 복귀 경로를 담은 로그인 링크를 만든다. */
export function loginHrefWithReturnTo(target: string): string {
  const safe = resolveReturnTo(target);
  return safe === null ? "/login" : `/login?returnTo=${encodeURIComponent(safe)}`;
}

/**
 * 클라이언트 액션이 현재 화면으로 돌아올 수 있는 로그인 링크를 만든다.
 * 이벤트 시점의 주소를 읽으므로 검색 조건과 해시도 함께 보존된다.
 */
export function loginHrefForCurrentPage(): string {
  if (typeof window === "undefined") {
    return "/login";
  }
  return loginHrefWithReturnTo(
    `${window.location.pathname}${window.location.search}${window.location.hash}`,
  );
}
