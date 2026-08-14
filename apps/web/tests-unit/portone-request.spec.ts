import { test, expect } from "@playwright/test";
import {
  EASY_PAY_PROVIDER,
  buildPortOneRequest,
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

const CONFIG = { storeId: "store-abc", channelKey: "channel-key-xyz" };

test.describe("buildPortOneRequest", () => {
  test("공통 필드를 그대로 싣는다", () => {
    const request = buildPortOneRequest(BASE, CONFIG);

    expect(request).toMatchObject({
      storeId: "store-abc",
      channelKey: "channel-key-xyz",
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

  test("카카오페이는 payMethod=EASY_PAY + 사업자 지정이다", () => {
    const request = buildPortOneRequest({ ...BASE, method: "KAKAOPAY" }, CONFIG);

    // 간편결제는 payMethod만으로 부족하다. 사업자를 지정하지 않으면 결제창이
    // 카드로 뜨거나 사업자 선택 화면이 한 단계 더 끼어든다.
    expect(request["payMethod"]).toBe("EASY_PAY");
    expect(request["easyPay"]).toEqual({ easyPayProvider: EASY_PAY_PROVIDER.KAKAOPAY });
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

  /** 사업자 식별자는 한 곳에서만 정의한다 — 콘솔 표기와 다르면 여기만 고치면 된다. */
  test("카카오페이 사업자 식별자는 상수 한 곳에서 온다", () => {
    expect(EASY_PAY_PROVIDER.KAKAOPAY).toBe("KAKAOPAY");
  });
});
