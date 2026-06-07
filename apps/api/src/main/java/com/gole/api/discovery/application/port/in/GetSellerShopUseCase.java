package com.gole.api.discovery.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 셀러 샵(셀러의 활성 리스팅). (요구사항 16.1, 16.2)
 */
public interface GetSellerShopUseCase {

    List<Listing> shopListings(String sellerId);
}
