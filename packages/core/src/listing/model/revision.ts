/**
 * 매물 수정·끌올 규칙 중 화면이 계산해야 하는 부분.
 *
 * 판정의 정본은 서버다(쿨다운·상태 검사). 여기서는 서버가 내려준 값을 사람이 읽을 형태로
 * 바꾸고, 롤아웃 중 새 필드가 빠진 응답을 안전하게 흡수하는 것만 한다.
 */
import type { Listing } from "./types";

/** 가격 인하 표시에 필요한 최소 형태. 상세 `Listing`과 디스커버리 요약이 함께 쓴다. */
export interface PricedListing {
  readonly price: number;
  readonly previousPrice?: number | null;
}

/**
 * 직전가보다 지금 가격이 싸면 내린 금액, 아니면 `null`.
 * 서버는 가격이 오르면 `previousPrice`를 비우지만, 같은 값이 와도 표시하지 않도록 한 번 더 막는다.
 */
export function priceDropAmount(listing: PricedListing): number | null {
  const previous = listing.previousPrice;
  if (previous === undefined || previous === null || !Number.isFinite(previous)) {
    return null;
  }
  return previous > listing.price ? previous - listing.price : null;
}

/** 금액만 원 단위로 쓴다. 예: 20000 → "20,000원" */
export function formatWon(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

/**
 * 끌올까지 남은 시간(ms). 끌올할 수 있거나 서버가 시각을 주지 않았으면 0이다.
 * 값이 없을 때 막지 않는 이유: 판정은 서버가 하고(429), 화면이 추측으로 버튼을 잠그면
 * 구 API 환경에서 끌올을 영영 못 누른다.
 */
export function bumpCooldownRemainingMs(
  listing: Pick<Listing, "bumpAvailableAt">,
  nowMs: number,
): number {
  if (listing.bumpAvailableAt === undefined) {
    return 0;
  }
  const availableAt = Date.parse(listing.bumpAvailableAt);
  if (Number.isNaN(availableAt)) {
    return 0;
  }
  return Math.max(0, availableAt - nowMs);
}

/**
 * 남은 시간을 "3시간 12분" · "12분" 처럼 분 단위로 쓴다. 1분이 안 남았어도 "1분"이다.
 * 올림이 아니라 반올림인 이유: 화면 시계가 틱 단위로 늦게 가므로 올림하면 "1시간"이
 * "1시간 1분"으로 보인다.
 */
export function formatCooldown(remainingMs: number): string {
  const totalMinutes = Math.max(1, Math.round(remainingMs / 60_000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) {
    return `${minutes}분`;
  }
  return minutes === 0 ? `${hours}시간` : `${hours}시간 ${minutes}분`;
}

const MEDIA_PATH_MARKER = "/api/v1/media/";

/** 공개 미디어 경로(`/api/v1/media/<키>`)에서 저장 키를 읽는다. 형태가 다르면 `null`. */
function mediaKeyFromUrl(url: string): string | null {
  const offset = url.indexOf(MEDIA_PATH_MARKER);
  if (offset < 0) {
    return null;
  }
  const key = url.slice(offset + MEDIA_PATH_MARKER.length).split(/[?#]/)[0] ?? "";
  return key.length > 0 ? key : null;
}

/**
 * 수정 폼이 다시 제출할 사진 키와 미리보기 URL 쌍.
 *
 * <p>새 API는 `photoKeys`를 함께 주지만 사용자 업로드가 아닌 사진(데모 커버 등)은 키에서 빠지므로
 * 순서로 짝짓지 않고 공개 경로의 키로 맞춘다. `photoKeys`가 없으면(구 API) 공개 경로에서 읽은 키를
 * 쓴다 — 커뮤니티 글 수정이 같은 방식으로 흡수한다. 키를 알 수 없는 사진은 다시 제출할 수 없으므로 뺀다.
 */
export function listingPhotos(
  listing: Pick<Listing, "photoUrls" | "photoKeys">,
): ReadonlyArray<{ readonly key: string; readonly url: string }> {
  const keys = listing.photoKeys;
  const paired = listing.photoUrls.flatMap((url) => {
    const key = mediaKeyFromUrl(url);
    if (key === null || (keys !== undefined && !keys.includes(key))) {
      return [];
    }
    return [{ key, url }];
  });
  // 공개 경로 형태를 읽지 못했지만 개수가 같다면 서버가 같은 순서로 준 것이다.
  if (
    keys !== undefined &&
    paired.length < keys.length &&
    keys.length === listing.photoUrls.length
  ) {
    return keys.map((key, index) => ({ key, url: listing.photoUrls[index] ?? "" }));
  }
  return paired;
}
