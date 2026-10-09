import { ApiError } from "../../runtime";
import { BID_ERROR } from "../model/types";

/** 오류 문구를 고를 때 무엇을 하다 실패했는지. */
export type BidAction = "place" | "cancel" | "fill" | "load";

const FALLBACK: Record<BidAction, string> = {
  place: "입찰을 걸지 못했어요. 잠시 후 다시 시도해 주세요.",
  cancel: "입찰을 취소하지 못했어요. 잠시 후 다시 시도해 주세요.",
  fill: "입찰가에 판매하지 못했어요. 잠시 후 다시 시도해 주세요.",
  load: "입찰 정보를 불러오지 못했어요.",
};

/**
 * 입찰 관련 실패를 사용자 문구로 바꾼다. 웹과 앱이 같은 문구를 쓰도록 코어에 둔다.
 * `BID_NOT_FOUND`는 취소(404)와 즉시 판매(409)에서 뜻이 다르므로 작업으로 가른다.
 */
export function bidErrorMessage(cause: unknown, action: BidAction): string {
  if (!(cause instanceof ApiError)) return FALLBACK[action];
  switch (cause.code) {
    case BID_ERROR.SET_NOT_FOUND:
      return "카탈로그에 없는 세트에는 입찰할 수 없어요.";
    case BID_ERROR.PRICE_INVALID:
      return "입찰가는 1원 이상 1억 원 이하로 입력해 주세요.";
    case BID_ERROR.DURATION_INVALID:
      return "입찰 기간은 7일·30일·60일 중에서 골라 주세요.";
    case BID_ERROR.CONDITION_INVALID:
      return "상태를 다시 골라 주세요.";
    case BID_ERROR.LIMIT_EXCEEDED:
      return "진행 중인 입찰은 30건까지 걸 수 있어요. 필요 없는 입찰을 취소한 뒤 다시 걸어 주세요.";
    case BID_ERROR.ACCESS_DENIED:
      return "내가 건 입찰만 취소할 수 있어요.";
    case BID_ERROR.NOT_ACTIVE:
      return "이미 끝난 입찰이에요.";
    case BID_ERROR.LISTING_MISMATCH:
      return "판매 중인 이 세트 매물만 입찰가에 팔 수 있어요.";
    case BID_ERROR.LISTING_ACCESS_DENIED:
      return "내가 등록한 매물만 입찰가에 팔 수 있어요.";
    case BID_ERROR.NOT_FOUND:
      return action === "fill"
        ? "지금 이 세트·상태에 받을 수 있는 입찰이 없어요. 내가 건 입찰은 제외돼요."
        : "입찰을 찾을 수 없어요.";
    default:
      return cause.message.length > 0 ? cause.message : FALLBACK[action];
  }
}
