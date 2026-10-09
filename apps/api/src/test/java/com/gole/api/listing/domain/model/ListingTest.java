package com.gole.api.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.TooManyRequestsException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import java.time.Duration;
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
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_INTEREST_TAG"));
        assertThatThrownBy(() -> InterestTag.fromKey("STAR_WARS"))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_INTEREST_TAG"));
    }

    // ── 수정 (listing-edit-and-bump E1, E3, E6) ──────────────────────────────

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration COOLDOWN = Duration.ofHours(24);

    private static ListingRevision revisionAt(long price) {
        return new ListingRevision(
                "수정한 제목",
                "수정한 설명",
                Money.won(price),
                ItemCondition.LIKE_NEW,
                new ConditionDisclosure(Completeness.FULL_BOX, true, true, false, "", ""),
                List.of("listing/new.jpg"),
                InterestTag.STAR_WARS);
    }

    @Test
    void revise_replacesEditableFieldsButNotSetCategoryOrListedAt() {
        Listing listing = listingWithSet("10307");

        listing.revise(revisionAt(10_000), CREATED.plusSeconds(60));

        assertThat(listing.getTitle()).isEqualTo("수정한 제목");
        assertThat(listing.getDescription()).isEqualTo("수정한 설명");
        assertThat(listing.getCondition()).isEqualTo(ItemCondition.LIKE_NEW);
        assertThat(listing.getDisclosure().completeness()).isEqualTo(Completeness.FULL_BOX);
        assertThat(listing.getPhotoUrls()).containsExactly("listing/new.jpg");
        assertThat(listing.getInterestTag()).isEqualTo(InterestTag.STAR_WARS);
        assertThat(listing.getCatalogSetNumber()).isEqualTo("10307");
        assertThat(listing.getCategory()).isEqualTo(ListingCategory.SET);
        assertThat(listing.getListedAt()).isEqualTo(CREATED);
    }

    @Test
    void revise_canClearInterestTag() {
        Listing listing = listing(InterestTag.TECHNIC);

        listing.revise(
                new ListingRevision(
                        "t", "d", Money.won(10_000), ItemCondition.USED_GOOD, null, List.of("listing/photo.jpg"), null),
                CREATED);

        assertThat(listing.getInterestTag()).isNull();
        assertThat(listing.getDisclosure()).isEqualTo(ConditionDisclosure.basic());
    }

    @Test
    void revise_reservedIsOrderInProgressConflict() {
        Listing listing = withStatus(ListingStatus.RESERVED);

        assertThatThrownBy(() -> listing.revise(revisionAt(1_000), CREATED))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
        assertThat(listing.getPrice().amount()).isEqualTo(10_000);
    }

    @Test
    void revise_soldAndDeletedAreNotEditable() {
        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.DELETED)) {
            Listing listing = withStatus(status);

            assertThatThrownBy(() -> listing.revise(revisionAt(1_000), CREATED))
                    .isInstanceOfSatisfying(
                            ConflictException.class,
                            error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_EDITABLE"));
        }
    }

    @Test
    void revise_rejectsEmptyPhotosWithoutChangingAnything() {
        Listing listing = listing(null);
        ListingRevision noPhotos =
                new ListingRevision("바뀌면 안 되는 제목", "d", Money.won(1), ItemCondition.USED_GOOD, null, List.of(), null);

        assertThatThrownBy(() -> listing.revise(noPhotos, CREATED)).isInstanceOf(MissingPhotoException.class);
        assertThat(listing.getTitle()).isEqualTo("테스트 매물");
        assertThat(listing.getPrice().amount()).isEqualTo(10_000);
    }

    @Test
    void revise_priceDropRecordsPreviousPriceAndChangeTime() {
        Listing listing = listing(null);
        Instant at = CREATED.plusSeconds(3_600);

        PriceChange change = listing.revise(revisionAt(8_000), at);

        assertThat(change.dropped()).isTrue();
        assertThat(change.before().amount()).isEqualTo(10_000);
        assertThat(change.after().amount()).isEqualTo(8_000);
        assertThat(listing.getPreviousPrice().amount()).isEqualTo(10_000);
        assertThat(listing.getPriceChangedAt()).isEqualTo(at);
    }

    @Test
    void revise_secondDropUsesTheImmediatelyPreviousPrice() {
        Listing listing = listing(null);
        listing.revise(revisionAt(8_000), CREATED.plusSeconds(1));

        listing.revise(revisionAt(7_000), CREATED.plusSeconds(2));

        assertThat(listing.getPreviousPrice().amount()).isEqualTo(8_000);
        assertThat(listing.getPriceChangedAt()).isEqualTo(CREATED.plusSeconds(2));
    }

    @Test
    void revise_priceRaiseClearsPreviousPrice() {
        Listing listing = listing(null);
        listing.revise(revisionAt(8_000), CREATED.plusSeconds(1));

        PriceChange change = listing.revise(revisionAt(9_000), CREATED.plusSeconds(2));

        assertThat(change.dropped()).isFalse();
        assertThat(change.raised()).isTrue();
        assertThat(listing.getPreviousPrice()).isNull();
        assertThat(listing.getPriceChangedAt()).isEqualTo(CREATED.plusSeconds(2));
    }

    @Test
    void revise_samePriceKeepsBothHistoryFields() {
        Listing listing = listing(null);
        listing.revise(revisionAt(8_000), CREATED.plusSeconds(1));

        PriceChange change = listing.revise(revisionAt(8_000), CREATED.plusSeconds(2));

        assertThat(change.dropped()).isFalse();
        assertThat(change.raised()).isFalse();
        assertThat(listing.getPreviousPrice().amount()).isEqualTo(10_000);
        assertThat(listing.getPriceChangedAt()).isEqualTo(CREATED.plusSeconds(1));
    }

    // ── 끌올 (listing-edit-and-bump B1~B3) ───────────────────────────────────

    @Test
    void listedAt_defaultsToCreatedAtForNewAndLegacyListings() {
        assertThat(listing(null).getListedAt()).isEqualTo(CREATED);
        assertThat(listing(null).getBumpedAt()).isNull();
        assertThat(withStatus(ListingStatus.ACTIVE).getListedAt()).isEqualTo(CREATED);
        assertThat(listing(null).bumpAvailableAt(COOLDOWN)).isEqualTo(CREATED.plus(COOLDOWN));
    }

    @Test
    void bump_isAllowedExactlyAtCooldownBoundary() {
        Listing listing = listing(null);
        Instant boundary = CREATED.plus(COOLDOWN);

        listing.bump(boundary, COOLDOWN);

        assertThat(listing.getListedAt()).isEqualTo(boundary);
        assertThat(listing.getBumpedAt()).isEqualTo(boundary);
        assertThat(listing.getCreatedAt()).isEqualTo(CREATED);
        assertThat(listing.bumpAvailableAt(COOLDOWN)).isEqualTo(boundary.plus(COOLDOWN));
    }

    @Test
    void bump_oneSecondBeforeBoundaryIsRejectedWithRetryAfter() {
        Listing listing = listing(null);

        assertThatThrownBy(() -> listing.bump(CREATED.plus(COOLDOWN).minusSeconds(1), COOLDOWN))
                .isInstanceOfSatisfying(TooManyRequestsException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("LISTING_BUMP_COOLDOWN");
                    assertThat(error.getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
                });
        assertThat(listing.getListedAt()).isEqualTo(CREATED);
        assertThat(listing.getBumpedAt()).isNull();
    }

    @Test
    void bump_rightAfterCreationIsRejected() {
        Listing listing = listing(null);

        assertThatThrownBy(() -> listing.bump(CREATED, COOLDOWN))
                .isInstanceOfSatisfying(
                        TooManyRequestsException.class,
                        error -> assertThat(error.getRetryAfter()).isEqualTo(COOLDOWN));
    }

    @Test
    void bump_reservedIsOrderInProgressAndOtherInactiveIsNotBumpable() {
        Instant later = CREATED.plus(COOLDOWN);
        assertThatThrownBy(() -> withStatus(ListingStatus.RESERVED).bump(later, COOLDOWN))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LISTING_ORDER_IN_PROGRESS"));
        for (ListingStatus status : List.of(ListingStatus.SOLD, ListingStatus.DELETED)) {
            assertThatThrownBy(() -> withStatus(status).bump(later, COOLDOWN))
                    .isInstanceOfSatisfying(
                            ConflictException.class,
                            error -> assertThat(error.getCode()).isEqualTo("LISTING_NOT_BUMPABLE"));
        }
    }

    @Test
    void revise_doesNotResetBumpCooldown() {
        Listing listing = listing(null);
        listing.bump(CREATED.plus(COOLDOWN), COOLDOWN);

        listing.revise(revisionAt(5_000), CREATED.plus(COOLDOWN).plusSeconds(10));

        assertThat(listing.getListedAt()).isEqualTo(CREATED.plus(COOLDOWN));
        assertThat(listing.getBumpedAt()).isEqualTo(CREATED.plus(COOLDOWN));
    }

    private static Listing withStatus(ListingStatus status) {
        return new Listing(
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
                status,
                CREATED);
    }

    private static Listing listingWithSet(String setNumber) {
        return Listing.create(
                "listing-1",
                "seller-1",
                "테스트 매물",
                "설명",
                Money.won(10_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                setNumber,
                ListingCategory.SET,
                null,
                CREATED);
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
