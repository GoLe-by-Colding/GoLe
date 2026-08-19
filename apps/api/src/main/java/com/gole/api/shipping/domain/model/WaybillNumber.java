package com.gole.api.shipping.domain.model;

import com.gole.api.shipping.domain.exception.InvalidWaybillException;
import java.util.Objects;

/**
 * 송장번호 값 객체. (R1.3)
 *
 * <p>정규화·검증을 생성자 불변식으로 강제한다: 공백 제거 → 숫자·하이픈만 허용 →
 * 하이픈을 걷어낸 숫자만 저장. 국내 택배사 송장은 10~13자리가 일반적이지만
 * 예외 포맷을 고려해 8~20자리를 허용한다.
 */
public record WaybillNumber(String value) {

    public WaybillNumber {
        Objects.requireNonNull(value, "value");
        String stripped = value.replaceAll("\\s", "");
        if (stripped.isEmpty()) {
            throw new InvalidWaybillException("송장번호를 입력해 주세요");
        }
        if (!stripped.matches("[0-9-]+")) {
            throw new InvalidWaybillException("송장번호는 숫자와 하이픈만 입력할 수 있습니다");
        }
        String digits = stripped.replace("-", "");
        if (digits.length() < 8 || digits.length() > 20) {
            throw new InvalidWaybillException("송장번호 자릿수를 확인해 주세요 (8~20자리)");
        }
        value = digits;
    }
}
