package com.gole.api.discovery.adapter.out.listing;

import com.gole.api.discovery.application.port.out.ListingQueryPort;
import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.domain.model.Listing;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CROSS-CONTEXT 어댑터: 디스커버리의 {@link ListingQueryPort} 출력 포트를
 * 리스팅 컨텍스트의 인바운드 유스케이스 {@link BrowseListingsUseCase}로 연결한다.
 *
 * <p>깨끗한 컨텍스트 경계: 디스커버리의 아웃바운드 어댑터가 리스팅의 인바운드 포트에만 의존하며,
 * 리스팅의 내부 도메인/영속성에는 직접 접근하지 않는다.
 */
@Component
public class ListingQueryAdapter implements ListingQueryPort {

    private final BrowseListingsUseCase browseListings;

    public ListingQueryAdapter(BrowseListingsUseCase browseListings) {
        this.browseListings = browseListings;
    }

    @Override
    public List<Listing> activeBySeller(String sellerId) {
        return browseListings.activeBySeller(sellerId);
    }

    @Override
    public List<Listing> activeBySellers(List<String> sellerIds, int limit) {
        return browseListings.activeBySellers(sellerIds, limit);
    }
}
