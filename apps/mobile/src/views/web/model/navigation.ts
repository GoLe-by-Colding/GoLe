/** 실행 원점과 탐색 정책. 토큰이나 임의 스크립트는 다루지 않는다. */
export function resolveWebOrigin(
  raw: string | undefined,
  development: boolean,
  android: boolean,
): string {
  const fallback = development
    ? android
      ? "http://10.0.2.2:3000"
      : "http://localhost:3000"
    : "https://gole.co.kr";
  const url = new URL(raw || fallback);
  if (
    url.username ||
    url.password ||
    (url.protocol !== "https:" && !(development && url.protocol === "http:"))
  ) {
    throw new Error("웹 주소는 배포에서 HTTPS, 개발에서 HTTP/HTTPS 원점이어야 합니다.");
  }
  return url.origin;
}

export function navigationTarget(raw: string, origin: string): "internal" | "external" | "blocked" {
  try {
    const url = new URL(raw);
    if (url.username || url.password) return "blocked";
    if (url.origin === origin && ["https:", "http:"].includes(url.protocol)) return "internal";
    if (["https:", "http:", "mailto:", "tel:"].includes(url.protocol)) return "external";
  } catch {
    /* 파싱 실패는 차단한다. */
  }
  return "blocked";
}
