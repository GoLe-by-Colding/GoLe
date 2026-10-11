package com.gole.api.discovery.adapter.out.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.discovery.domain.model.DiscoveredListing;
import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingQueryAdapterTest {

    private static final Instant T0 = Instant.parse("2026-10-10T00:00:00Z");

    private final BrowseListingsUseCase browse = mock(BrowseListingsUseCase.class);
    private final ListingQueryAdapter adapter = new ListingQueryAdapter(browse);

    @Test
    @DisplayName("매물을 샵·피드 요약 값으로 환원한다")
    void activeBySeller_mapsListingToSummary() {
        Listing listing = new Listing(
                "listing-1",
                "seller-1",
                "에펠탑 10307",
                "미개봉",
                Money.won(280_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("images/a.jpg"),
                "10307",
                ListingCategory.SET,
                ListingStatus.ACTIVE,
                T0);
        when(browse.activeBySeller("seller-1")).thenReturn(List.of(listing));

        assertThat(adapter.activeBySeller("seller-1"))
                .containsExactly(new DiscoveredListing(
                        "listing-1",
                        "seller-1",
                        "에펠탑 10307",
                        280_000,
                        "NEW_SEALED",
                        "10307",
                        "SET",
                        "ACTIVE",
                        List.of("images/a.jpg"),
                        T0,
                        listing.getListedAt(),
                        null));
    }
}
