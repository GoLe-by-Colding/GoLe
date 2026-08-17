package com.gole.api.order.domain.model;

/**
 * 결제수단 분류. PG 중립적인 도메인 어휘이며, 특정 PG의 표기와 1:1이 아니다.
 *
 * <p>{@link #UNKNOWN}은 오류가 아니라 <b>정상적인 값</b>이다. PG가 새 수단을 추가하거나 응답
 * 형태를 바꿨을 때, 결제 자체를 실패시키는 대신 분류만 모른 채로 승인을 진행하기 위한 자리다.
 */
public enum PaymentMethodType {

    /** 신용·체크카드. */
    CARD,

    /** 간편결제(카카오페이·네이버페이·토스페이 등). 실제 사업자는 provider가 가진다. */
    EASY_PAY,

    /** 가상계좌 입금. */
    VIRTUAL_ACCOUNT,

    /** 계좌이체. */
    TRANSFER,

    /** 휴대폰 소액결제. */
    MOBILE,

    /** 상품권·포인트류. */
    GIFT_CERTIFICATE,

    /** 분류 불명. PG가 알려주지 않았거나 우리가 모르는 수단이다. */
    UNKNOWN
}
