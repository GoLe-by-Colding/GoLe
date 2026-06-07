package com.gole.api.discovery.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.util.List;

/**
 * Inbound port: 팔로우한 셀러의 활성 리스팅 개인화 피드. (요구사항 16.6, 16.7)
 */
public interface GetPersonalizedFeedUseCase {

    List<Listing> feed(String userId);
}
