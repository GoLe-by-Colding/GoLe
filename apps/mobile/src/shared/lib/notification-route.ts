/** 서버의 웹 경로를 현재 RN 라우트로 환원한다. 외부 URL은 절대 열지 않는다. */
export function notificationRoute(value: unknown): string | null {
  if (typeof value !== "string" || !value.startsWith("/") || value.length > 2048) return null;
  let path: string;
  try {
    path = decodeURIComponent(value);
  } catch {
    return null;
  }
  if (path.startsWith("//") || /[\\\s%?#]/.test(path)) return null;
  if (path.split("/").some((segment) => segment === "." || segment === "..")) return null;
  const detail = /^\/(listings?|sets?|sellers?)\/([A-Za-z0-9_-]+)$/.exec(path);
  if (detail) {
    const section = detail[1]?.replace(/s$/, "");
    return `/${section}/${detail[2]}`;
  }
  if (["/", "/search", "/chat", "/me", "/notifications", "/community"].includes(path)) {
    return path;
  }
  return "/notifications";
}
