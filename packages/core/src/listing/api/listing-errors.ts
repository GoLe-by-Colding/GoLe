import { ApiError } from "../../runtime";
import { formatCooldown } from "../model/revision";

/** 매물 수정·끌올이 돌려줄 수 있는 오류 코드. 서버 `ListingErrorCode`와 같은 이름이다. */
export const LISTING_ERROR_CODES = {
  accessDenied: "LISTING_ACCESS_DENIED",
  orderInProgress: "LISTING_ORDER_IN_PROGRESS",
  notEditable: "LISTING_NOT_EDITABLE",
  notBumpable: "LISTING_NOT_BUMPABLE",
  bumpCooldown: "LISTING_BUMP_COOLDOWN",
} as const;

export type ListingMutation = "edit" | "bump";

/**
 * 수정·끌올 실패를 사용자 문구로 바꾼다. 웹과 앱이 같은 문구를 쓰도록 코어에 둔다.
 * 알 수 없는 코드는 서버 메시지를, 네트워크 오류는 작업별 기본 문구를 쓴다.
 */
export function listingMutationErrorMessage(cause: unknown, mutation: ListingMutation): string {
  if (!(cause instanceof ApiError)) {
    return mutation === "edit"
      ? "수정 내용을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
      : "끌올하지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
  switch (cause.code) {
    case LISTING_ERROR_CODES.accessDenied:
      return "내가 등록한 매물만 수정하거나 끌올할 수 있어요.";
    case LISTING_ERROR_CODES.orderInProgress:
      return mutation === "edit"
        ? "거래가 진행 중인 매물은 수정할 수 없어요."
        : "거래가 진행 중인 매물은 끌올할 수 없어요.";
    case LISTING_ERROR_CODES.notEditable:
      return "판매가 끝났거나 내린 매물은 수정할 수 없어요.";
    case LISTING_ERROR_CODES.notBumpable:
      return "판매 중인 매물만 끌올할 수 있어요.";
    case LISTING_ERROR_CODES.bumpCooldown:
      return cause.retryAfterMs !== null && cause.retryAfterMs > 0
        ? `아직 끌올할 수 없어요. ${formatCooldown(cause.retryAfterMs)} 후에 다시 할 수 있어요.`
        : "아직 끌올할 수 없어요. 마지막 등록·끌올 뒤 쿨다운이 끝나면 다시 시도해 주세요.";
    default:
      return cause.message.length > 0
        ? cause.message
        : mutation === "edit"
          ? "수정 내용을 저장하지 못했어요."
          : "끌올하지 못했어요.";
  }
}
