package com.gole.api.listing.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Inbound port: 운영 화면이 보는 매물 현황. 일반 검색과 달리 삭제된 매물까지 모든 상태를 본다. 상태·분류는 저장된
 * 이름 그대로 문자열로 낸다.
 */
public interface MonitorListingsUseCase {

    /** 판매 중(ACTIVE) 매물 수. */
    long activeListingCount();

    /** 최근 매물. status 가 null 이거나 비면 전체, query 는 매물 ID·제목·판매자 ID·분류의 부분 일치. */
    List<ListingMonitorRow> recentListings(String status, String query, int limit);

    /** 전체 매물 수(추정치). */
    long estimatedListingCount();

    record ListingMonitorRow(
            String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {}
}
