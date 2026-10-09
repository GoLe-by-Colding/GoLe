package com.gole.api.order.adapter.out.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import com.gole.api.order.application.port.out.ListingReservationPort.ReservedListing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ListingReservationAdapterTest {

    @Test
    void reserve_passesSetNumberOnlyForWholeSetSoCompletedOrdersFeedTheRightPrice() {
        ReservedListing set = reserve(listing(ListingCategory.SET));
        ReservedListing minifig = reserve(listing(ListingCategory.MINIFIG));
        ReservedListing parts = reserve(listing(ListingCategory.PARTS));

        // 완료 주문은 이 세트 번호로 체결가가 기록된다. 미니피규어·부품 가격이 세트 한 벌 시세에 섞이면 안 된다.
        assertThat(set.catalogSetNumber()).isEqualTo("75192");
        assertThat(minifig.catalogSetNumber()).isNull();
        assertThat(parts.catalogSetNumber()).isNull();
        assertThat(minifig.price()).isEqualTo(30_000);
        assertThat(minifig.condition()).isEqualTo("used_good");
    }

    private static ReservedListing reserve(Listing listing) {
        ListingReservationAdapter adapter = new ListingReservationAdapter(
                listingId -> Optional.of(listing), listingId -> {}, new MarkListingSoldUseCase() {
                    @Override
                    public void markSold(String listingId) {}

                    @Override
                    public boolean markDirectTradeSoldIfActive(String listingId) {
                        return false;
                    }
                });
        return adapter.reserve(listing.getId()).orElseThrow();
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
                ListingStatus.RESERVED,
                Instant.parse("2026-10-09T00:00:00Z"));
    }
}
