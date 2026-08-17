package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 결제수단 값 객체.
 *
 * <p>"간편결제"라는 분류만으로는 부족하다. 카카오페이인지 네이버페이인지는 수수료 협상,
 * 환불 응대, 정산 대사에서 모두 다르게 취급되므로 사업자(provider)를 함께 보관한다.
 */
class PaymentMethodTest {

    @Test
    void easyPay_carriesProvider() {
        PaymentMethod method = new PaymentMethod(PaymentMethodType.EASY_PAY, "KAKAOPAY");

        assertThat(method.type()).isEqualTo(PaymentMethodType.EASY_PAY);
        assertThat(method.provider()).isEqualTo("KAKAOPAY");
    }

    /** 같은 사업자가 대소문자만 달라 다른 값처럼 집계되면 안 된다. */
    @Test
    void provider_isNormalizedToUpperCase() {
        assertThat(new PaymentMethod(PaymentMethodType.EASY_PAY, "kakaopay").provider())
                .isEqualTo("KAKAOPAY");
    }

    /** 빈 문자열은 "사업자를 모른다"와 같은 뜻이다 — null로 접어 두 표현이 갈라지지 않게 한다. */
    @Test
    void blankProvider_collapsesToNull() {
        assertThat(new PaymentMethod(PaymentMethodType.CARD, "").provider()).isNull();
        assertThat(new PaymentMethod(PaymentMethodType.CARD, "   ").provider()).isNull();
        assertThat(new PaymentMethod(PaymentMethodType.CARD, null).provider()).isNull();
    }

    @Test
    void type_isRequired() {
        assertThatThrownBy(() -> new PaymentMethod(null, "KAKAOPAY")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void of_createsProviderlessMethod() {
        assertThat(PaymentMethod.of(PaymentMethodType.CARD).provider()).isNull();
        assertThat(PaymentMethod.of(PaymentMethodType.CARD).type()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void unknown_isProviderless() {
        assertThat(PaymentMethod.UNKNOWN.type()).isEqualTo(PaymentMethodType.UNKNOWN);
        assertThat(PaymentMethod.UNKNOWN.provider()).isNull();
    }
}
