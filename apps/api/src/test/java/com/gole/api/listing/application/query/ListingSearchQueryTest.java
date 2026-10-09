package com.gole.api.listing.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.domain.model.InterestTag;
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

    @Test
    void interestTagInText_readsKoreanLabelsKeysAndSpacing() {
        assertThat(text("스타워즈").interestTagInText()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(text(" 스타 워즈 ").interestTagInText()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(text("Star Wars").interestTagInText()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(text("star-wars").interestTagInText()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(text("해리포터").interestTagInText()).isEqualTo(InterestTag.HARRY_POTTER);
    }

    @Test
    void interestTagInText_ignoresPartialNamesAndOtherWords() {
        assertThat(text("스타").interestTagInText()).isNull();
        assertThat(text("스타워즈 밀레니엄").interestTagInText()).isNull();
        assertThat(text("75192").interestTagInText()).isNull();
        assertThat(text("   ").interestTagInText()).isNull();
        assertThat(new ListingSearchQuery(null, null, null, null, null).interestTagInText())
                .isNull();
    }

    private static ListingSearchQuery text(String text) {
        return new ListingSearchQuery(text, null, null, null, null);
    }
}
