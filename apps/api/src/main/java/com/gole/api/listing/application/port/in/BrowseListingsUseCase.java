package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 셀러 샵/피드/위시리스트 구성을 위한 리스팅 조회. (요구사항 16, 17)
 */
public interface BrowseListingsUseCase {

    List<Listing> activeBySeller(String sellerId);

    /**
     * 본인 매물 전체(상태 무관, 최신순). "내 매물"은 판매완료·예약중도 보여야 하므로
     * 활성만 주는 {@link #activeBySeller}와 구분한다. 남에게 노출하면 안 되는 조회다.
     */
    List<Listing> bySeller(String sellerId);

    List<Listing> activeBySellers(List<String> sellerIds);

    List<Listing> byIds(List<String> ids);
}
