package com.gole.api.discovery.adapter.out.listing;

import com.gole.api.discovery.application.port.out.ListingQueryPort;
import com.gole.api.discovery.domain.model.DiscoveredListing;
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
    public List<DiscoveredListing> activeBySeller(String sellerId) {
        return toDiscovered(browseListings.activeBySeller(sellerId));
    }

    @Override
    public List<DiscoveredListing> activeBySellers(List<String> sellerIds, int limit) {
        return toDiscovered(browseListings.activeBySellers(sellerIds, limit));
    }

    private static List<DiscoveredListing> toDiscovered(List<Listing> listings) {
        return listings.stream().map(ListingQueryAdapter::toDiscovered).toList();
    }

    private static DiscoveredListing toDiscovered(Listing l) {
        return new DiscoveredListing(
                l.getId(),
                l.getSellerId(),
                l.getTitle(),
                l.getPrice().amount(),
                l.getCondition().name(),
                l.getCatalogSetNumber(),
                l.getCategory().name(),
                l.getStatus().name(),
                l.getPhotoUrls(),
                l.getCreatedAt(),
                l.getListedAt(),
                l.getPreviousPrice() == null ? null : l.getPreviousPrice().amount());
    }
}
