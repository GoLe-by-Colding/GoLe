package com.gole.api.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PhoneNumberTest {

    @Test
    void normalizesToDigitsOnly() {
        assertThat(new PhoneNumber("010-1234-5678").value()).isEqualTo("01012345678");
        assertThat(new PhoneNumber("02 123 4567").value()).isEqualTo("021234567");
    }

    @Test
    void masksMiddleDigits() {
        assertThat(new PhoneNumber("010-1234-5678").masked()).isEqualTo("010-****-5678");
        assertThat(new PhoneNumber("021234567").masked()).isEqualTo("02-****-4567");
    }

    @Test
    void rejectsInvalidFormats() {
        assertThatThrownBy(() -> new PhoneNumber("12345")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new PhoneNumber("019876543210000")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new PhoneNumber("phone")).isInstanceOf(BadRequestException.class);
        // 존재하지 않는 휴대폰 국번(013)
        assertThatThrownBy(() -> new PhoneNumber("013-1234-5678")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void ofNullable_returnsNullForBlank() {
        assertThat(PhoneNumber.ofNullable(null)).isNull();
        assertThat(PhoneNumber.ofNullable("  ")).isNull();
        assertThat(PhoneNumber.ofNullable("010-1234-5678")).isNotNull();
    }
}
