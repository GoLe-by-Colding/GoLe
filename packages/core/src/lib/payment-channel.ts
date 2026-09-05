/**
 * 결제수단 ↔ 포트원 채널 짝짓기. <b>웹과 앱이 반드시 같은 규칙을 써야 한다.</b>
 *
 * <p>서버는 원장의 채널 키로 어느 허용 채널인지 먼저 정하고, 그 채널이 낼 수 있는 결제수단만
 * 인정한다. 클라이언트가 다른 조합을 보내면 결제는 열리지만 검증에서 전부 `PAYMENT_REVIEW`로
 * 떨어진다 — 실패가 아니라 <b>수동 검토 적체</b>로 나타나므로 조용하다. 그래서 SDK가 플랫폼별로
 * 달라도 이 규칙만은 한 곳에 둔다.
 */

/** 구매자가 고른 결제수단. 채널과 payMethod가 이 값 하나로 함께 정해진다. */
export type PortOneMethod = "kakaopay" | "card";

export type PortOnePayMethod = "EASY_PAY" | "CARD";

export interface PortOneChannelKeys {
  /** 카카오페이 채널. */
  readonly kakaopay: string;
  /** 카드(KG이니시스) 채널. 빈 문자열이면 카드는 닫힌 것이다 — 오류가 아니다. */
  readonly card: string;
}

export interface ResolvedChannel {
  readonly channelKey: string;
  readonly payMethod: PortOnePayMethod;
}

/** 고른 수단에 맞는 채널 키와 payMethod를 함께 돌려준다. 둘을 따로 고르게 하지 않는다. */
export function resolveChannel(method: PortOneMethod, keys: PortOneChannelKeys): ResolvedChannel {
  if (method === "card") {
    if (keys.card.length === 0) {
      throw new Error("카드 결제 채널이 설정되지 않았습니다.");
    }
    return { channelKey: keys.card, payMethod: "CARD" };
  }
  if (keys.kakaopay.length === 0) {
    throw new Error("카카오페이 결제 채널이 설정되지 않았습니다.");
  }
  return { channelKey: keys.kakaopay, payMethod: "EASY_PAY" };
}

/**
 * 카드 결제에만 필요한 구매자 정보.
 *
 * KG이니시스는 카드결제에서 이름·연락처·이메일을 요구한다. 카카오페이는 어느 것도 요구하지
 * 않으므로 카드를 고른 경우에만 수집한다.
 */
export interface PortOneCustomer {
  readonly fullName: string;
  readonly email: string;
  readonly phoneNumber: string;
}

/**
 * 카드 결제에 필요한 구매자 정보를 결제창을 열기 <b>전에</b> 확정한다.
 *
 * 빠진 채로 결제창을 열면 이니시스가 PG 오류로 튕기는데, 그 화면은 무엇이 빠졌는지 알려주지
 * 않는다. 여기서 막으면 사용자가 고칠 수 있는 말로 알려줄 수 있다.
 */
export function requireCardCustomer(customer: PortOneCustomer | undefined): PortOneCustomer {
  const fullName = customer?.fullName.trim() ?? "";
  const email = customer?.email.trim() ?? "";
  const phoneNumber = customer?.phoneNumber.trim() ?? "";
  const missing = [
    fullName.length === 0 ? "이름" : null,
    email.length === 0 ? "이메일" : null,
    phoneNumber.length === 0 ? "연락처" : null,
  ].filter((label): label is string => label !== null);
  if (missing.length > 0) {
    throw new Error(`카드 결제에는 ${missing.join("·")}가 필요합니다.`);
  }
  return { fullName, email, phoneNumber };
}

/** 결제 금액은 원 단위 양의 정수여야 한다. 플랫폼과 무관한 계약이다. */
export function requireValidAmount(totalAmount: number): number {
  if (!Number.isSafeInteger(totalAmount) || totalAmount <= 0) {
    throw new Error("결제 금액이 올바르지 않습니다.");
  }
  return totalAmount;
}
