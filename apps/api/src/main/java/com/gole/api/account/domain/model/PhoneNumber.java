package com.gole.api.account.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.Objects;

/**
 * 계정 본인 확인용 휴대폰 번호 값 객체. (onboarding D4)
 *
 * <p>{@code order.PhoneNumber}(CS 연락처)와 형태가 닮았지만 의도적으로 복제했다 —
 * 컨텍스트 경계를 넘어 참조하지 않으며, 이쪽은 OTP를 받을 수 있어야 하므로 지역 유선번호를
 * 허용하지 않고 휴대폰({@code 01[016789]})으로만 좁힌다.
 *
 * <p>저장은 항상 숫자만. 하이픈·공백 표기는 표현 계층의 일이다.
 */
public record PhoneNumber(String value) {

    public PhoneNumber {
        Objects.requireNonNull(value, "value");
        String digits = value.replaceAll("[\\s-]", "");
        if (!digits.matches("01[016789]\\d{7,8}")) {
            throw new BadRequestException("INVALID_PHONE", "휴대폰 번호 형식을 확인해 주세요 (예: 010-1234-5678)");
        }
        value = digits;
    }

    /**
     * 기본 노출용 마스킹: {@code 01012345678 → 010-****-5678}.
     *
     * <p>마스킹을 화면단이 아니라 도메인에 두는 이유 — 새 화면을 만들 때마다 빠뜨릴 수 있고
     * 실수의 대가가 개인정보 노출이기 때문이다.
     */
    public String masked() {
        return value.substring(0, 3) + "-****-" + value.substring(value.length() - 4);
    }

    /** null 허용 정규화 헬퍼. 빈 값이면 null을 반환한다. */
    public static PhoneNumber ofNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new PhoneNumber(raw);
    }
}
