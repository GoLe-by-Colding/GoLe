package com.gole.api.bid.adapter.out.listing;

import com.gole.api.bid.application.port.out.BidListingPort;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingStatus;
import org.springframework.stereotype.Component;

/**
 * 리스팅 컨텍스트 통합 어댑터. {@link BidListingPort}를 {@link GetListingUseCase}로 위임하고 리스팅 도메인 객체를
 * 입찰 체결에 필요한 최소 데이터로 환원한다. 없는 매물은 리스팅의 404를 그대로 올린다.
 */
@Component
public class ListingBidListingAdapter implements BidListingPort {

    private final GetListingUseCase getListing;

    public ListingBidListingAdapter(GetListingUseCase getListing) {
        this.getListing = getListing;
    }

    @Override
    public BidListing get(String listingId) {
        Listing listing = getListing.getById(listingId);
        return new BidListing(
                listing.getId(),
                listing.getSellerId(),
                listing.getCatalogSetNumber(),
                listing.getCondition().key(),
                listing.getPrice().amount(),
                listing.getStatus() == ListingStatus.ACTIVE);
    }
}
