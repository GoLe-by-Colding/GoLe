import { ApiError } from "../../runtime";
import { OFFER_ERROR } from "../model/types";

/** 오류 문구를 고를 때 무엇을 하다 실패했는지. */
export type OfferAction = "make" | "accept" | "decline" | "withdraw" | "load" | "order";

const FALLBACK: Record<OfferAction, string> = {
  make: "가격을 제안하지 못했어요. 잠시 후 다시 시도해 주세요.",
  accept: "제안을 수락하지 못했어요. 잠시 후 다시 시도해 주세요.",
  decline: "제안을 거절하지 못했어요. 잠시 후 다시 시도해 주세요.",
  withdraw: "제안을 철회하지 못했어요. 잠시 후 다시 시도해 주세요.",
  load: "가격 제안을 불러오지 못했어요.",
  order: "주문을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
};

function retryHint(cause: ApiError): string {
  if (cause.retryAfterMs === null || cause.retryAfterMs <= 0) return "";
  const hours = Math.ceil(cause.retryAfterMs / 3_600_000);
  return hours <= 1
    ? " 1시간쯤 뒤에 다시 시도해 주세요."
    : ` ${hours}시간쯤 뒤에 다시 시도해 주세요.`;
}

/**
 * 제안 관련 실패를 사용자 문구로 바꾼다. 웹과 앱이 같은 문구를 쓰도록 코어에 둔다.
 * 알 수 없는 코드는 서버 메시지를, 네트워크 오류는 작업별 기본 문구를 쓴다.
 */
export function offerErrorMessage(cause: unknown, action: OfferAction): string {
  if (!(cause instanceof ApiError)) return FALLBACK[action];
  switch (cause.code) {
    case OFFER_ERROR.ROOM_NOT_LISTING:
      return "매물 대화방에서만 가격을 제안할 수 있어요.";
    case OFFER_ERROR.BUYER_ONLY:
      return "구매자만 가격을 제안할 수 있어요.";
    case OFFER_ERROR.LISTING_UNAVAILABLE:
      return "판매 중인 매물이 아니라 가격 제안을 진행할 수 없어요.";
    case OFFER_ERROR.PRICE_INVALID:
      return "제안 금액은 0원보다 크고 판매가보다 낮아야 해요.";
    case OFFER_ERROR.ALREADY_PENDING:
      return "이 매물에 응답을 기다리는 제안이 이미 있어요. 응답을 기다리거나 철회한 뒤 다시 제안해 주세요.";
    case OFFER_ERROR.RATE_LIMITED:
      return `같은 매물에는 하루에 제안할 수 있는 횟수를 넘었어요.${retryHint(cause)}`;
    case OFFER_ERROR.NOT_PENDING:
      return "이미 응답했거나 만료된 제안이에요.";
    case OFFER_ERROR.NOT_OPEN:
      return "이미 끝난 제안이에요.";
    case OFFER_ERROR.ACCESS_DENIED:
      return "이 제안에 대한 권한이 없어요.";
    case OFFER_ERROR.NOT_FOUND:
      return "제안을 찾을 수 없어요.";
    case OFFER_ERROR.NOT_USABLE:
      return "이 제안으로는 주문할 수 없어요. 제안이 만료됐거나 취소됐을 수 있어요.";
    default:
      return cause.message.length > 0 ? cause.message : FALLBACK[action];
  }
}
