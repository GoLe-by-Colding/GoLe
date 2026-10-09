import { apiRequest } from "../../runtime";
import type { Bid, BidBook, FillBidResult, PlaceBidInput } from "../model/types";

const BASE = "/api/v1/bids";

function withSignal(signal: AbortSignal | undefined): { readonly signal?: AbortSignal } {
  return signal === undefined ? {} : { signal };
}

function bookPath(setNumber: string): string {
  return `${BASE}/book/${encodeURIComponent(setNumber.trim())}`;
}

/**
 * 입찰을 건다. 같은 세트·상태의 진행 중 입찰이 있으면 새로 만들지 않고 가격·기간을 갱신한다(D3) —
 * 응답은 둘 다 200이고, 갱신인지는 {@link isBidRenewal}로 가른다.
 * 온보딩 미완료면 서버가 403 `ONBOARDING_REQUIRED`로 막고 공용 래퍼가 온보딩으로 안내한다.
 */
export function placeBid(input: PlaceBidInput): Promise<Bid> {
  return apiRequest<Bid>(BASE, {
    method: "POST",
    body: {
      setNumber: input.setNumber.trim(),
      condition: input.condition,
      price: input.price,
      ...(input.durationDays === undefined ? {} : { durationDays: input.durationDays }),
    },
  });
}

/** 본인의 진행 중 입찰만 취소한다(D4). 성공하면 204. */
export function cancelBid(bidId: string): Promise<void> {
  return apiRequest<void>(`${BASE}/${encodeURIComponent(bidId)}`, { method: "DELETE" });
}

/** 내 입찰 최신순 최대 100건, 만료를 반영한 유효 상태(D5). 세션 필요. */
export function fetchMyBids(signal?: AbortSignal): Promise<readonly Bid[]> {
  return apiRequest<readonly Bid[]>(`${BASE}/mine`, { cache: "no-store", ...withSignal(signal) });
}

/** 공개 호가창(D6). 입찰을 건 직후처럼 최신이 필요한 클라이언트 조회용. */
export function fetchBidBook(setNumber: string, signal?: AbortSignal): Promise<BidBook> {
  return apiRequest<BidBook>(bookPath(setNumber), { cache: "no-store", ...withSignal(signal) });
}

/**
 * 서버 렌더 화면(세트 상세)용 호가창. 세트 상세는 재검증(ISR)으로 그려지므로 `no-store`를 쓰면
 * 페이지 전체가 매 요청 렌더로 바뀐다. 부품 요청 건수와 같이 1분 재검증으로 읽는다.
 */
export function fetchBidBookForPage(setNumber: string): Promise<BidBook> {
  return apiRequest<BidBook>(bookPath(setNumber), { next: { revalidate: 60 } });
}

/**
 * 판매자가 자기 판매 중 매물을 그 세트·상태의 최고 입찰가(본인 입찰 제외)에 판다(D7).
 * 입찰자에게 그 가격의 수락 제안이 생긴다. 판매자 신원확인이 필요하다.
 */
export function fillBid(setNumber: string, listingId: string): Promise<FillBidResult> {
  return apiRequest<FillBidResult>(`${bookPath(setNumber)}/fill`, {
    method: "POST",
    body: { listingId },
  });
}
