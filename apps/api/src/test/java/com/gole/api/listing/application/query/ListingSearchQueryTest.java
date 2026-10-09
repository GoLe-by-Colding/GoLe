package com.gole.api.listing.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListingSearchQueryTest {

    @Test
    void setNumberInText_readsPlainHashAndVariantNumbers() {
        assertThat(text("75192").setNumberInText()).isEqualTo("75192");
        assertThat(text(" #75192 ").setNumberInText()).isEqualTo("75192");
        // 브릭링크·리브릭커블식 변형 번호는 같은 세트다.
        assertThat(text("10307-1").setNumberInText()).isEqualTo("10307");
        assertThat(text("910").setNumberInText()).isEqualTo("910");
    }

    @Test
    void setNumberInText_ignoresWordsMixedTextAndImplausibleNumbers() {
        assertThat(text("밀레니엄").setNumberInText()).isNull();
        assertThat(text("UCS 75192").setNumberInText()).isNull();
        assertThat(text("12").setNumberInText()).isNull();
        assertThat(text("12345678").setNumberInText()).isNull();
        assertThat(text("10307-123").setNumberInText()).isNull();
        assertThat(new ListingSearchQuery(null, null, null, null, null).setNumberInText())
                .isNull();
    }

    private static ListingSearchQuery text(String text) {
        return new ListingSearchQuery(text, null, null, null, null);
    }
}
