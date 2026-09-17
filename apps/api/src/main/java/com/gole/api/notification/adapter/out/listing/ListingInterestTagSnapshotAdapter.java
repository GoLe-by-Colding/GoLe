package com.gole.api.notification.adapter.out.listing;

import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.notification.application.port.out.ListingSnapshotPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** listing 인바운드 포트를 notification 매물 스냅샷 포트로 변환한다. */
@Component
public class ListingInterestTagSnapshotAdapter implements ListingSnapshotPort {

    private final GetListingUseCase listings;

    public ListingInterestTagSnapshotAdapter(GetListingUseCase listings) {
        this.listings = listings;
    }

    @Override
    public Optional<ListingSnapshot> findById(String listingId) {
        try {
            Listing listing = listings.getById(listingId);
            return Optional.of(new ListingSnapshot(listing.getId(), listing.isActive()));
        } catch (ListingNotFoundException ignored) {
            return Optional.empty();
        }
    }
}
