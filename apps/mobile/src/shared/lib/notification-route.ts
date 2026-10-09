/** 알림이 열 수 있는 공개 서비스 경로. API·관리자·외부 URL은 허용하지 않는다. */
export function notificationWebPath(value: unknown): string | null {
  if (typeof value !== "string" || !value.startsWith("/") || value.length > 2048) return null;
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    return null;
  }
  if (decoded.startsWith("//") || /[\\\u0000-\u001f\u007f]/.test(decoded) || /\s/.test(value))
    return null;
  const path = value.split(/[?#]/, 1)[0] ?? "";
  // 경로의 인코딩·정규화로 허용 목록을 우회하지 못하게 한다. query/hash는 그대로 보존한다.
  if (path.includes("%")) return null;
  if (path.split("/").some((segment) => segment === "." || segment === "..")) return null;
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
