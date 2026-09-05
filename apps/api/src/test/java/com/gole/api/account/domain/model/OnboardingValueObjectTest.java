package com.gole.api.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/** 닉네임(D9)·휴대폰 번호(D4) 값 객체 불변식 검증. */
class OnboardingValueObjectTest {

    @Test
    void nicknameAcceptsKoreanEnglishAndDigits() {
        assertThat(new Nickname("고레").value()).isEqualTo("고레");
        assertThat(new Nickname("GoLe123").value()).isEqualTo("GoLe123");
        assertThat(new Nickname("  고레마스터  ").value()).isEqualTo("고레마스터"); // 앞뒤 공백은 다듬는다
    }

    @Test
    void nicknameRejectsLengthOutsideTwoToTwelve() {
        assertThatThrownBy(() -> new Nickname("고"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2~12자");
        assertThatThrownBy(() -> new Nickname("가".repeat(13))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nicknameRejectsWhitespaceAndSymbols() {
        // 공백을 허용하면 "고 레"와 "고레"가 서로 다른 이름으로 공존한다.
        assertThatThrownBy(() -> new Nickname("고 레")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new Nickname("go-le")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new Nickname("고레!")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void nicknameUniquenessKeyIgnoresCase() {
        assertThat(new Nickname("GoLe").normalized()).isEqualTo(new Nickname("gole").normalized());
    }

    @Test
    void phoneNumberNormalizesToDigitsOnly() {
        assertThat(new PhoneNumber("010-1234-5678").value()).isEqualTo("01012345678");
        assertThat(new PhoneNumber("010 1234 5678").value()).isEqualTo("01012345678");
    }

    @Test
    void phoneNumberRejectsLandline() {
        // OTP를 받아야 하므로 지역 유선번호(order 쪽 CS 연락처와 달리)는 허용하지 않는다.
        assertThatThrownBy(() -> new PhoneNumber("021234567")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> new PhoneNumber("0312345678")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void phoneNumberRejectsUnknownMobilePrefix() {
        assertThatThrownBy(() -> new PhoneNumber("01212345678")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void phoneNumberMasksMiddleDigits() {
        assertThat(new PhoneNumber("01012345678").masked()).isEqualTo("010-****-5678");
    }
}
