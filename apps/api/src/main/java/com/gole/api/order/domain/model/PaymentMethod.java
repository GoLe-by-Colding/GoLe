package com.gole.api.order.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 결제수단. 결제 승인 시점에 PG가 알려준 <b>사실</b>이며, 프론트가 요청한 의도가 아니다.
 *
 * <p>분류({@link PaymentMethodType})만으로는 부족해 사업자({@code provider})를 함께 보관한다.
 * "간편결제"라는 사실은 카카오페이와 네이버페이를 구분하지 못하는데, 수수료 협상·환불 응대·
 * 정산 대사는 모두 그 구분 위에서 이루어진다.
 *
 * @param type 결제수단 분류. null 불가(모르면 {@link PaymentMethodType#UNKNOWN}).
 * @param provider 간편결제 사업자 식별자(대문자, 예: {@code KAKAOPAY}). 해당 없으면 null.
 */
public record PaymentMethod(PaymentMethodType type, String provider) {

    /** PG가 결제수단을 알려주지 않았을 때 쓰는 값. */
    public static final PaymentMethod UNKNOWN = new PaymentMethod(PaymentMethodType.UNKNOWN, null);

    public PaymentMethod {
        Objects.requireNonNull(type, "type");
        // 빈 문자열과 null은 둘 다 "사업자를 모른다"는 뜻이다. 두 표현이 저장소에 섞이면
        // 집계에서 서로 다른 값으로 갈라지므로 null 하나로 접는다.
        provider = (provider == null || provider.isBlank())
                ? null
                : provider.trim().toUpperCase(Locale.ROOT);
    }

    /** 사업자 구분이 없는 결제수단(카드·가상계좌 등). */
    public static PaymentMethod of(PaymentMethodType type) {
        return new PaymentMethod(type, null);
    }
}
