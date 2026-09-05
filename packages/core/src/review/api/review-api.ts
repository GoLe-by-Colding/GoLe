import { apiRequest } from "../../runtime";
import type { Review, SellerRating } from "../model/types";

export interface WriteReviewPayload {
  readonly orderId: string;
  readonly reviewerId: string;
  readonly rating: number;
  readonly content: string;
}

/** 후기 작성. (요구사항 R1) */
export function writeReview(payload: WriteReviewPayload): Promise<Review> {
  return apiRequest<Review>("/api/v1/reviews", {
    method: "POST",
    body: payload,
  });
}

/** 후기를 받은 판매자의 공개 답글. 서버가 로그인 계정과 revieweeId 일치를 검증한다. */
export function replyToReview(reviewId: string, content: string): Promise<Review> {
  return apiRequest<Review>(`/api/v1/reviews/${reviewId}/reply`, {
    method: "POST",
    body: { content },
  });
}

/** 특정 셀러의 후기 목록(최신순). (요구사항 R3.1) */
export function fetchSellerReviews(
  sellerId: string,
  signal?: AbortSignal,
): Promise<readonly Review[]> {
  return apiRequest<readonly Review[]>(`/api/v1/sellers/${sellerId}/reviews`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}

/** 특정 셀러의 평점 요약(평균/후기 수). (요구사항 R3.2) */
export function fetchSellerRating(sellerId: string, signal?: AbortSignal): Promise<SellerRating> {
  return apiRequest<SellerRating>(`/api/v1/sellers/${sellerId}/rating`, {
    cache: "no-store",
    ...(signal === undefined ? {} : { signal }),
  });
}
