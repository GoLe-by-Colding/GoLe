package com.gole.api.order.adapter.out.listing;

import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.ReleaseListingUseCase;
import com.gole.api.listing.application.port.in.ReserveListingUseCase;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.order.application.port.out.ListingReservationPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 리스팅 컨텍스트 통합 어댑터. 주문 컨텍스트의 {@link ListingReservationPort}를
 * 리스팅 인바운드 유스케이스로 위임 구현한다.
 *
 * <p>선점 결과인 도메인 {@link Listing}을 주문 컨텍스트가 필요로 하는 최소 데이터
 * ({@link ListingReservationPort.ReservedListing})로 환원해 컨텍스트 간 결합을 끊는다.
 */
@Component
public class ListingReservationAdapter implements ListingReservationPort {

    private final ReserveListingUseCase reserveListing;
    private final ReleaseListingUseCase releaseListing;
    private final MarkListingSoldUseCase markListingSold;

    public ListingReservationAdapter(
            ReserveListingUseCase reserveListing,
            ReleaseListingUseCase releaseListing,
            MarkListingSoldUseCase markListingSold) {
        this.reserveListing = reserveListing;
        this.releaseListing = releaseListing;
        this.markListingSold = markListingSold;
    }

    @Override
    public Optional<ReservedListing> reserve(String listingId) {
        return reserveListing.reserve(listingId).map(this::toReservedListing);
    }

    @Override
    public void release(String listingId) {
        releaseListing.release(listingId);
    }

    @Override
    public void markSold(String listingId) {
        markListingSold.markSold(listingId);
    }

    private ReservedListing toReservedListing(Listing listing) {
        return new ReservedListing(
                listing.getId(),
                listing.getSellerId(),
                listing.getCatalogSetNumber(),
                listing.getPrice().amount(),
                listing.getCondition().name().toLowerCase());
    }
}
