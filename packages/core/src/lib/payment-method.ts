/**
 * 결제수단 표기. 백엔드 PaymentMethodType/provider와 대응한다.
 *
 * 구매자 API와 관리자 API가 같은 표기(대문자 열거형 이름)를 쓰므로 매핑 테이블은 여기 한 벌이다.
 */
export interface PaymentMethod {
  readonly type: string;
  /** 간편결제 사업자(KAKAOPAY 등). 해당 없으면 null. */
  readonly provider: string | null;
}

const TYPE_LABEL: Readonly<Record<string, string>> = {
  CARD: "카드",
  EASY_PAY: "간편결제",
  VIRTUAL_ACCOUNT: "가상계좌",
  TRANSFER: "계좌이체",
  MOBILE: "휴대폰",
  GIFT_CERTIFICATE: "상품권",
  UNKNOWN: "확인 불가",
};

const PROVIDER_LABEL: Readonly<Record<string, string>> = {
  KAKAOPAY: "카카오페이",
  NAVERPAY: "네이버페이",
  TOSSPAY: "토스페이",
  PAYCO: "페이코",
  SAMSUNGPAY: "삼성페이",
  APPLEPAY: "애플페이",
  SSGPAY: "SSG페이",
  LPAY: "엘페이",
};

/**
 * 결제수단을 사람이 읽는 말로 옮긴다. 결제 전 주문은 "—".
 *
 * 간편결제는 분류명보다 사업자명이 사용자가 실제로 아는 이름이다. "간편결제"라고만 적으면
 * 카카오페이로 결제한 사람이 자기 결제를 못 알아본다.
 *
 * `undefined`도 "결제수단 없음"으로 받는다. 결제수단을 내보내기 전 버전의 응답이나 필드를 생략한
 * 목(mock)에서는 필드 자체가 없으므로, `null`만 걸러내면 주문 화면 전체가 렌더 중 예외로 죽는다.
 */
export function paymentMethodLabel(method: PaymentMethod | null | undefined): string {
  if (method === null || method === undefined) {
    return "—";
  }
  if (method.provider !== null && method.provider !== undefined) {
    return PROVIDER_LABEL[method.provider] ?? method.provider;
  }
  return TYPE_LABEL[method.type] ?? method.type;
}
