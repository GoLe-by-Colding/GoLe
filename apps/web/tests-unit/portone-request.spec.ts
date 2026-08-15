import { test, expect } from "@playwright/test";
import {
  availablePaymentChoices,
  buildPortOneRequest,
  type PortOneChannelKeys,
  type PortOnePayParams,
} from "../src/shared/lib/portone-request";

/**
 * 포트원 결제 요청 페이로드 조립 — 브라우저 없이 도는 순수 함수 테스트.
 *
 * <p>결제창에 무엇을 넘기는지가 결제수단을 결정한다. 여기가 틀리면 카카오페이를 골라도
 * 카드창이 뜨거나, 모바일에서 결제 후 돌아올 곳이 없어진다. 실제 결제 계정 없이 검증할 수
 * 있는 유일한 구간이므로 단위 테스트로 못 박는다.
 */

const BASE: PortOnePayParams = {
  paymentId: "order-1234",
  orderName: "GoLe 주문 order-12",
  totalAmount: 280000,
  method: "CARD",
  redirectUrl: "https://gole.kscold.com/orders/order-1234",
};

const CHANNEL_KEYS: PortOneChannelKeys = {
  CARD: "channel-key-card",
  KAKAOPAY: "channel-key-kakao",
};

const CONFIG = { storeId: "store-abc", channelKeys: CHANNEL_KEYS };

test.describe("buildPortOneRequest", () => {
  test("공통 필드를 그대로 싣는다", () => {
    const request = buildPortOneRequest(BASE, CONFIG);

    expect(request).toMatchObject({
      storeId: "store-abc",
      paymentId: "order-1234",
      orderName: "GoLe 주문 order-12",
      totalAmount: 280000,
      currency: "CURRENCY_KRW",
    });
  });

  test("카드 결제는 payMethod=CARD이고 easyPay를 붙이지 않는다", () => {
    const request = buildPortOneRequest({ ...BASE, method: "CARD" }, CONFIG);

    expect(request["payMethod"]).toBe("CARD");
    expect(request["easyPay"]).toBeUndefined();
  });

  /**
   * 카카오페이는 PG사 자체가 간편결제사다. 전용 채널이 이미 사업자를 결정하므로
   * easyPayProvider를 실을 필요가 없고, 포트원도 이 값을 무시한다.
   */
  test("카카오페이는 payMethod=EASY_PAY이고 사업자를 따로 싣지 않는다", () => {
    const request = buildPortOneRequest({ ...BASE, method: "KAKAOPAY" }, CONFIG);

    expect(request["payMethod"]).toBe("EASY_PAY");
    expect(request["easyPay"]).toBeUndefined();
  });

  /**
   * 포트원은 PG사마다 채널이 따로다. 결제수단을 고르는 것은 곧 채널을 고르는 것이라,
   * 채널 키 하나를 모든 수단에 재사용하면 한쪽 결제가 통째로 실패한다.
   */
  test("결제수단에 맞는 채널 키를 고른다", () => {
    expect(buildPortOneRequest({ ...BASE, method: "CARD" }, CONFIG)["channelKey"]).toBe(
      "channel-key-card",
    );
    expect(buildPortOneRequest({ ...BASE, method: "KAKAOPAY" }, CONFIG)["channelKey"]).toBe(
      "channel-key-kakao",
    );
  });

  /** 설정 누락은 결제 실패가 아니라 설정 문제다. 원인이 드러나는 자리에서 끊는다. */
  test("채널 키가 없는 결제수단은 요청을 만들지 않는다", () => {
    const cardOnly = {
      storeId: "store-abc",
      channelKeys: { CARD: "channel-key-card", KAKAOPAY: "" },
    };

    expect(() => buildPortOneRequest({ ...BASE, method: "KAKAOPAY" }, cardOnly)).toThrow(
      /카카오페이/,
    );
  });

  /**
   * 모바일은 결제창이 별도 페이지로 이동했다가 돌아오는 방식이라 복귀 주소가 없으면
   * 결제 후 사용자가 빈 화면에 남는다. 데스크톱에서만 테스트하면 절대 발견되지 않는다.
   */
  test("결제수단과 무관하게 redirectUrl을 항상 싣는다", () => {
    for (const method of ["CARD", "KAKAOPAY"] as const) {
      const request = buildPortOneRequest({ ...BASE, method }, CONFIG);
      expect(request["redirectUrl"]).toBe("https://gole.kscold.com/orders/order-1234");
    }
  });

  test("금액은 원 단위 정수 그대로 전달한다", () => {
    const request = buildPortOneRequest({ ...BASE, totalAmount: 1 }, CONFIG);

    expect(request["totalAmount"]).toBe(1);
  });
});

test.describe("availablePaymentChoices", () => {
  test("채널 키가 설정된 수단만 남긴다", () => {
    expect(availablePaymentChoices(CHANNEL_KEYS)).toEqual(["CARD", "KAKAOPAY"]);
    expect(availablePaymentChoices({ CARD: "channel-key-card", KAKAOPAY: "" })).toEqual(["CARD"]);
    expect(availablePaymentChoices({ CARD: "", KAKAOPAY: "channel-key-kakao" })).toEqual([
      "KAKAOPAY",
    ]);
  });

  /** 하나도 없으면 포트원 자체가 꺼진 것으로 본다(서버 스텁이 결제를 승인한다). */
  test("설정이 없으면 빈 목록이다", () => {
    expect(availablePaymentChoices({ CARD: "", KAKAOPAY: "" })).toEqual([]);
  });
});
