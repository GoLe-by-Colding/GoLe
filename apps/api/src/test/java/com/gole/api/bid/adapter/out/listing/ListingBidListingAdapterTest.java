package com.gole.api.bid.adapter.out.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.bid.application.port.out.BidListingPort.BidListing;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingBidListingAdapterTest {

    @Test
    void get_setNumberOnlyForWholeSetSoMinifigCannotFillSetBid() {
        BidListing set = get(listing(ListingCategory.SET));
        BidListing minifig = get(listing(ListingCategory.MINIFIG));

        // 세트 입찰 체결은 매물 세트 번호가 입찰 세트와 같아야 한다 — 미니피규어는 null이라 "맞지 않는 매물"로 거절된다.
        assertThat(set.setNumber()).isEqualTo("75192");
        assertThat(minifig.setNumber()).isNull();
        assertThat(minifig.active()).isTrue();
    }

    private static BidListing get(Listing listing) {
        ListingBidListingAdapter adapter = new ListingBidListingAdapter(new GetListingUseCase() {
            @Override
            public Listing getById(String listingId) {
                return listing;
            }

            @Override
            public Listing getPublicById(String listingId) {
                return listing;
            }
        });
        return adapter.get(listing.getId());
    }

    private static Listing listing(ListingCategory category) {
        return new Listing(
                "listing-" + category.key(),
                "seller-1",
                "75192 매물",
                "설명",
                Money.won(30_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                "75192",
                category,
                ListingStatus.ACTIVE,
                Instant.parse("2026-10-09T00:00:00Z"));
    }
}
