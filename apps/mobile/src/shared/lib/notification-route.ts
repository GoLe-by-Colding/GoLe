/**
 * 앱 안에서 따라갈 수 있는 상대 경로인지 — 알림 링크와 탭 이동 메시지가 같이 쓴다.
 * `//host`·백슬래시·제어 문자·공백, 경로의 퍼센트 인코딩과 점 세그먼트처럼 원점·허용 목록을
 * 우회할 수 있는 모양을 모두 막는다. query/hash는 그대로 보존한다.
 */
export function isSafeAppPath(value: unknown): value is string {
  if (typeof value !== "string" || !value.startsWith("/") || value.length > 2048) return false;
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    return false;
  }
  if (decoded.startsWith("//") || /[\\\u0000-\u001f\u007f]/.test(decoded) || /\s/.test(value))
    return false;
  const path = value.split(/[?#]/, 1)[0] ?? "";
  if (path.includes("%")) return false;
  return !path.split("/").some((segment) => segment === "." || segment === "..");
}

/** 알림이 열 수 있는 공개 서비스 경로. API·관리자·외부 URL은 허용하지 않는다. */
export function notificationWebPath(value: unknown): string | null {
  if (!isSafeAppPath(value)) return null;
  const path = value.split(/[?#]/, 1)[0] ?? "";
  const roots = [
    "/",
    "/search",
    "/chat",
    "/me",
    "/profile",
    "/notifications",
    "/community",
    "/parts",
    "/collection",
    "/sell",
    "/feed",
    "/prices",
  ];
  if (
    roots.includes(path) ||
    /^\/(listings?|sets?|sellers?|shops|orders|parts|community)\/[A-Za-z0-9_-]+$/.test(path)
  ) {
    return value;
  }
  return null;
}

/** 웹의 query/hash를 잃지 않고 RN 화면으로 이동한다. */
export function notificationRoute(value: unknown): string | null {
  const path = notificationWebPath(value);
  if (path === null) return null;
  const detail = /^\/(listings?|sets?|sellers?)\/([A-Za-z0-9_-]+)$/.exec(path);
  if (detail) {
    const section = detail[1]?.replace(/s$/, "");
    return `/${section}/${detail[2]}`;
  }
  if (["/", "/search", "/chat", "/me", "/notifications", "/community"].includes(path)) {
    return path;
  }
  const canonical = path.replace(
    /^\/(listing|set|sellers?)\//,
    (_, section: string) => `/${section.startsWith("seller") ? "shops" : `${section}s`}/`,
  );
  return `/web?path=${encodeURIComponent(canonical)}`;
}
