package com.gole.api.discovery.application.port.out;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Outbound port (CROSS-CONTEXT): 디스커버리가 리스팅 컨텍스트의 리스팅 데이터를 조회한다.
 * 셀러 샵/개인화 피드 구성을 위한 활성 리스팅 조회. (요구사항 16.1, 16.2, 16.6, 16.7)
 *
 * <p>구현 어댑터는 리스팅 컨텍스트의 인바운드 유스케이스를 호출한다.
 */
public interface ListingQueryPort {

    /** 특정 셀러의 활성 리스팅. */
    List<Listing> activeBySeller(String sellerId);

    /** 여러 셀러의 활성 리스팅을 최신순으로 제한해 조회한다. */
    List<Listing> activeBySellers(List<String> sellerIds, int limit);
}
