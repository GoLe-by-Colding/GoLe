package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 셀러 샵/피드/위시리스트 구성을 위한 리스팅 조회. (요구사항 16, 17)
 */
public interface BrowseListingsUseCase {

    List<Listing> activeBySeller(String sellerId);

    List<Listing> activeBySellers(List<String> sellerIds);

    List<Listing> byIds(List<String> ids);
}
