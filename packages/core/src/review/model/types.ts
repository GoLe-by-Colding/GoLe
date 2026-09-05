/**
 * 거래 후기 도메인 타입. 백엔드 ReviewResponse / SellerRatingResponse와 1:1 대응.
 */
export interface Review {
  readonly id: string;
  readonly orderId: string;
  readonly reviewerId: string;
  readonly revieweeId: string;
  readonly rating: number;
  readonly content: string;
  readonly createdAt: string;
  readonly reply: string | null;
  readonly repliedAt: string | null;
}

export interface SellerRating {
  readonly sellerId: string;
  readonly average: number;
  readonly count: number;
}
