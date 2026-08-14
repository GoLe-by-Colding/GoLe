package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.util.Locale;
import java.util.Map;

/**
 * 포트원 V2 결제 응답의 {@code method} 객체를 도메인 {@link PaymentMethod}로 옮긴다.
 *
 * <p>포트원의 표기(예: {@code PaymentMethodEasyPay})는 어댑터 밖으로 새어나가지 않는다.
 *
 * <p><b>전사(total) 함수다.</b> 어떤 입력에도 예외를 던지지 않고 최악의 경우
 * {@link PaymentMethod#UNKNOWN}을 돌려준다. 결제수단 분류를 못 했다는 이유로 이미 돈을 낸
 * 구매자의 승인 검증이 무너지면, 잃는 것이 정보가 아니라 주문이기 때문이다.
 */
final class PortOnePaymentMethodMapper {

    private static final String TYPE_PREFIX = "PAYMENTMETHOD";

    private PortOnePaymentMethodMapper() {}

    static PaymentMethod from(Map<?, ?> payment) {
        if (payment == null || !(payment.get("method") instanceof Map<?, ?> method)) {
            return PaymentMethod.UNKNOWN;
        }
        if (!(method.get("type") instanceof String rawType)) {
            return PaymentMethod.UNKNOWN;
        }
        PaymentMethodType type = toType(rawType);
        // 사업자는 간편결제에서만 의미가 있다. 카드 결제에 딸려온 값을 그대로 담으면
        // "카카오페이 결제 건수" 같은 집계가 조용히 오염된다.
        return type == PaymentMethodType.EASY_PAY
                ? new PaymentMethod(type, easyPayProvider(method))
                : PaymentMethod.of(type);
    }

    /**
     * 표기 흔들림을 흡수한다. 접두사 유무({@code PaymentMethodEasyPay} vs {@code EASY_PAY}),
     * 대소문자, 구분자(스네이크)가 달라도 같은 수단으로 읽는다.
     */
    private static PaymentMethodType toType(String raw) {
        String normalized = raw.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (normalized.startsWith(TYPE_PREFIX)) {
            normalized = normalized.substring(TYPE_PREFIX.length());
        }
        return switch (normalized) {
            case "CARD" -> PaymentMethodType.CARD;
            case "EASYPAY" -> PaymentMethodType.EASY_PAY;
            case "VIRTUALACCOUNT" -> PaymentMethodType.VIRTUAL_ACCOUNT;
            case "TRANSFER" -> PaymentMethodType.TRANSFER;
            case "MOBILE" -> PaymentMethodType.MOBILE;
            case "GIFTCERTIFICATE" -> PaymentMethodType.GIFT_CERTIFICATE;
            default -> PaymentMethodType.UNKNOWN;
        };
    }

    /** 중첩({@code easyPay.provider})과 평평한 키({@code easyPayProvider}) 양쪽을 받아낸다. */
    private static String easyPayProvider(Map<?, ?> method) {
        if (method.get("easyPay") instanceof Map<?, ?> easyPay && easyPay.get("provider") instanceof String nested) {
            return nested;
        }
        return method.get("easyPayProvider") instanceof String flat ? flat : null;
    }
}
