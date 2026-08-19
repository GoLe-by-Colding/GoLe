package com.gole.api.order.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.Objects;

/**
 * CS 연락처 전화번호 값 객체. (shipping-and-fees R8.3)
 *
 * <p>정규화(숫자만)·형식 검증을 생성자 불변식으로 강제한다. 국내 휴대폰(01x)과
 * 지역 유선(0xx)을 허용한다. 저장은 항상 숫자만 — 하이픈·공백 표기는 표현 계층의 일이다.
 */
public record PhoneNumber(String value) {

    public PhoneNumber {
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

    /**
     * 기본 노출용 마스킹. 가운데 자리를 가린다: {@code 01012345678 → 010-****-5678}. (R8.4)
     *
     * <p>마스킹을 화면단이 아니라 여기(도메인)에 두는 이유 — 새 화면을 만들 때마다
     * 마스킹을 빠뜨릴 수 있고, 실수의 대가가 개인정보 노출이기 때문이다.
     */
    public String masked() {
        int tail = 4;
        int head = value.length() >= 10 ? 3 : 2;
        return value.substring(0, head) + "-****-" + value.substring(value.length() - tail);
    }

    /** null 허용 정규화 헬퍼. 빈 값이면 null을 반환한다. */
    public static PhoneNumber ofNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new PhoneNumber(raw);
    }
}
