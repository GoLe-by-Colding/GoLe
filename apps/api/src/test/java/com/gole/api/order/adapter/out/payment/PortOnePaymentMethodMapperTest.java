package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.order.domain.model.PaymentMethod;
import com.gole.api.order.domain.model.PaymentMethodType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 포트원 V2 결제 응답 → 도메인 결제수단 매핑.
 *
 * <p>이 매퍼는 <b>전사(total) 함수</b>여야 한다. 포트원이 결제수단을 새로 추가하거나 필드 이름을
 * 바꿨다는 이유로 결제 검증이 예외로 무너지면, 이미 돈을 낸 구매자의 주문이 실패 처리된다.
 * 모르는 값은 UNKNOWN으로 접는다 — 정보를 잃을지언정 결제를 잃지는 않는다.
 */
class PortOnePaymentMethodMapperTest {

    private static PaymentMethod fromMethod(Map<String, Object> method) {
        return PortOnePaymentMethodMapper.from(Map.of("method", method));
    }

    @Test
    void card() {
        assertThat(fromMethod(Map.of("type", "PaymentMethodCard")).type()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void easyPay_extractsNestedProvider() {
        PaymentMethod method =
                fromMethod(Map.of("type", "PaymentMethodEasyPay", "easyPay", Map.of("provider", "KAKAOPAY")));

        assertThat(method.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(method.provider()).isEqualTo("KAKAOPAY");
    }

    /** provider를 중첩이 아니라 평평한 키로 주는 응답 형태도 받아낸다. */
    @Test
    void easyPay_extractsFlatProvider() {
        PaymentMethod method = fromMethod(Map.of("type", "PaymentMethodEasyPay", "easyPayProvider", "NAVERPAY"));

        assertThat(method.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(method.provider()).isEqualTo("NAVERPAY");
    }

    /** 접두사 유무·대소문자·스네이크 표기가 흔들려도 같은 결제수단으로 읽혀야 한다. */
    @Test
    void typeMatching_toleratesNamingVariants() {
        for (String raw : List.of("PaymentMethodEasyPay", "EASY_PAY", "easyPay", "EasyPay", "easy_pay")) {
            assertThat(fromMethod(Map.of("type", raw)).type())
                    .describedAs("type=%s", raw)
                    .isEqualTo(PaymentMethodType.EASY_PAY);
        }
    }

    @Test
    void otherKnownTypes() {
        assertThat(fromMethod(Map.of("type", "PaymentMethodVirtualAccount")).type())
                .isEqualTo(PaymentMethodType.VIRTUAL_ACCOUNT);
        assertThat(fromMethod(Map.of("type", "PaymentMethodTransfer")).type()).isEqualTo(PaymentMethodType.TRANSFER);
        assertThat(fromMethod(Map.of("type", "PaymentMethodMobile")).type()).isEqualTo(PaymentMethodType.MOBILE);
        assertThat(fromMethod(Map.of("type", "PaymentMethodGiftCertificate")).type())
                .isEqualTo(PaymentMethodType.GIFT_CERTIFICATE);
    }

    @Test
    void unknownOrMissing_foldsToUnknown() {
        assertThat(PortOnePaymentMethodMapper.from(null)).isEqualTo(PaymentMethod.UNKNOWN);
        assertThat(PortOnePaymentMethodMapper.from(Map.of())).isEqualTo(PaymentMethod.UNKNOWN);
        assertThat(fromMethod(Map.of("type", "PaymentMethodTeleport")).type()).isEqualTo(PaymentMethodType.UNKNOWN);
    }

    /** method 자체가 문자열이거나 형태가 어긋나도 예외 없이 UNKNOWN이어야 한다. */
    @Test
    void malformedShape_foldsToUnknown() {
        assertThat(PortOnePaymentMethodMapper.from(Map.of("method", "PaymentMethodCard")))
                .isEqualTo(PaymentMethod.UNKNOWN);
        assertThat(PortOnePaymentMethodMapper.from(Map.of("method", Map.of("type", 42))))
                .isEqualTo(PaymentMethod.UNKNOWN);
    }

    /** 간편결제가 아닌데 provider가 딸려오면 버린다 — 카드사명이 간편결제 사업자로 집계되면 통계가 오염된다. */
    @Test
    void provider_isKeptOnlyForEasyPay() {
        PaymentMethod method = fromMethod(Map.of("type", "PaymentMethodCard", "easyPayProvider", "KAKAOPAY"));

        assertThat(method.type()).isEqualTo(PaymentMethodType.CARD);
        assertThat(method.provider()).isNull();
    }
}
