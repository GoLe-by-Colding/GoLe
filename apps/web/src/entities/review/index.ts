export type { Review, SellerRating } from "./model/types";
export {
  writeReview,
  fetchSellerReviews,
  fetchSellerRating,
} from "./api/review-api";
export type { WriteReviewPayload } from "./api/review-api";
