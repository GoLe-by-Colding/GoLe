package com.gole.api.bid.adapter.out.listing;

import com.gole.api.bid.application.port.out.BidListingPort;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.RaiseListingPriceUseCase;
import com.gole.api.listing.domain.exception.ListingConflictException;
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
    private final RaiseListingPriceUseCase raisePrice;

    public ListingBidListingAdapter(GetListingUseCase getListing, RaiseListingPriceUseCase raisePrice) {
        this.getListing = getListing;
        this.raisePrice = raisePrice;
    }

    @Override
    public BidListing get(String listingId) {
        Listing listing = getListing.getById(listingId);
        return new BidListing(
                listing.getId(),
                listing.getSellerId(),
                // 세트 입찰은 세트 한 벌로만 체결한다 — 출처 세트 번호를 단 미니피규어·부품은 맞지 않는 매물이다.
                listing.wholeSetNumber(),
                listing.getCondition().key(),
                listing.getPrice().amount(),
                listing.getStatus() == ListingStatus.ACTIVE);
    }

    @Override
    public void raisePriceTo(String listingId, String sellerId, long expectedPrice, long targetPrice) {
        try {
            raisePrice.raisePriceTo(listingId, sellerId, expectedPrice, targetPrice);
        } catch (ListingConflictException conflict) {
            if (!ListingConflictException.PRICE_CHANGED.equals(conflict.getCode())) {
                throw conflict;
            }
            throw BidErrors.priceChanged();
        }
    }
}
