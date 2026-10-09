import type { Listing } from "@entities/listing";
import {
  listingPriceGap,
  priceComparableSetNumber,
  priceGapLabel,
  type PriceSnapshot,
} from "@entities/pricing";

/** 한 화면에서 시세를 함께 조회할 세트 수 상한. 목록이 길어도 시세 조회가 화면을 붙잡지 않게 한다. */
export const PRICE_NOTE_SET_LIMIT = 24;

/** 목록의 세트 번호를 앞에서부터 겹치지 않게 모은다(판매 중·예약 중인 세트 한 벌 매물만). */
export function priceNoteSetNumbers(listings: readonly Listing[]): string[] {
  const sets = new Set<string>();
  for (const listing of listings) {
    if (sets.size >= PRICE_NOTE_SET_LIMIT) break;
    const setNumber = priceComparableSetNumber(listing);
    if (setNumber !== null) sets.add(setNumber);
  }
  return [...sets];
}

/**
 * 매물 카드에 붙일 "추정 시세보다 N% 낮음/높음" 한 줄(매물 id → 문구).
 * 같은 등급의 실제 표본 추정 시세가 있을 때만 만든다(`listingPriceGap`). 출처 경고가 있으면 "참고용"을 붙인다.
 */
export function buildPriceNotes(
  listings: readonly Listing[],
  snapshots: Readonly<Record<string, PriceSnapshot | null>>,
): Record<string, string> {
  const notes: Record<string, string> = {};
  for (const listing of listings) {
    const setNumber = priceComparableSetNumber(listing);
    if (setNumber === null) continue;
    const gap = listingPriceGap(listing.price, listing.condition, snapshots[setNumber] ?? null);
    if (gap === null) continue;
    const label = priceGapLabel(gap.ratio, "추정 시세보다");
    notes[listing.id] = gap.evidenceWarning === null ? label : `${label} · 참고용`;
  }
  return notes;
}
