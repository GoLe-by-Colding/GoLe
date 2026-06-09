/**
 * 미디어 이미지 URL에 썸네일 폭(?w=)을 부여한다. (백로그 N2a)
 *
 * <p>GoLe 미디어 엔드포인트(`/api/v1/media/`) URL에만 적용하고, 그 외(플레이스홀더·외부 URL)는
 * 그대로 반환한다. 서버는 미지원 포맷이면 원본을 제공하므로 안전하다.
 */
export function thumbnailUrl(url: string, width: number): string {
  if (!url.includes("/api/v1/media/")) {
    return url;
  }
  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}w=${width}`;
}
