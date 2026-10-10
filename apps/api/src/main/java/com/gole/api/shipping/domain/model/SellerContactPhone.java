package com.gole.api.shipping.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.Objects;

/**
 * 판매자 CS 연락처. (shipping-and-fees R8.3)
 *
 * <p>정규화(숫자만)·형식 검증을 생성자 불변식으로 강제한다. 국내 휴대폰(01x)과 지역 유선(0xx)을 허용한다. 주문의 연락처 값 객체와
 * 같은 규칙이지만 배송 컨텍스트가 자기 값으로 갖는다 — 다른 컨텍스트의 도메인 객체를 빌려 쓰지 않는다.
 */
public record SellerContactPhone(String value) {

    public SellerContactPhone {
        Objects.requireNonNull(value, "value");
        String digits = value.replaceAll("[\\s-]", "");
        if (!digits.matches("0\\d{8,10}")) {
            throw new BadRequestException("INVALID_PHONE", "연락 가능한 전화번호를 확인해 주세요 (예: 010-1234-5678)");
        }
        if (digits.startsWith("01") && !digits.matches("01[016789]\\d{7,8}")) {
            throw new BadRequestException("INVALID_PHONE", "휴대폰 번호 형식을 확인해 주세요");
        }
        value = digits;
    }
}
