package com.gole.api.offer.adapter.out.listing;

import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.offer.application.port.out.OfferListingPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 리스팅 컨텍스트 통합 어댑터. 제안의 {@link OfferListingPort}를 리스팅 인바운드 유스케이스
 * {@link GetListingUseCase}로 위임 구현하고, 리스팅 도메인 객체를 제안에 필요한 최소 데이터로 환원한다.
 */
@Component
public class OfferListingAdapter implements OfferListingPort {

    private final GetListingUseCase getListing;

    public OfferListingAdapter(GetListingUseCase getListing) {
        this.getListing = getListing;
    }

    @Override
    public Optional<OfferListing> find(String listingId) {
        try {
            Listing listing = getListing.getById(listingId);
            return Optional.of(new OfferListing(
                    listing.getId(),
                    listing.getSellerId(),
                    listing.getPrice().amount(),
                    listing.getStatus() == ListingStatus.ACTIVE));
        } catch (ListingNotFoundException missing) {
            return Optional.empty();
        }
    }
}
