package com.gole.api.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingTest {

    @Test
    void interestTag_allowsNullAndPreservesValue() {
        Listing withoutTag = listing(null);
        Listing withTag = listing(InterestTag.TECHNIC);

        assertThat(withoutTag.getInterestTag()).isNull();
        assertThat(withTag.getInterestTag()).isEqualTo(InterestTag.TECHNIC);
    }

    @Test
    void interestTagFromKey_allowsBlankAsUnspecified() {
        assertThat(InterestTag.fromKey(null)).isNull();
        assertThat(InterestTag.fromKey("  ")).isNull();
    }

    @Test
    void interestTagFromKey_rejectsUnknownKey() {
        assertThatThrownBy(() -> InterestTag.fromKey("unknown-theme"))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode())
                        .isEqualTo("INVALID_INTEREST_TAG"));
    }

    private static Listing listing(InterestTag interestTag) {
        return Listing.create(
                "listing-1",
                "seller-1",
                "테스트 매물",
                "설명",
                Money.won(10_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                null,
                ListingCategory.SET,
                interestTag,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
