package com.gole.api.admin.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Outbound port: 운영 화면용 <b>읽기 전용</b> 조회. (admin-console 요구사항 9.2)
 *
 * <p>대시보드 집계와 모니터링 목록은 도메인 애그리거트가 아니라 리포팅용 read model이다.
 * 이를 포트 뒤에 두어 컨트롤러가 MongoDB(또는 어떤 저장소든)를 직접 알지 못하게 한다.
 *
 * <p>반환 record는 관리자 컨텍스트 소유다. 타 컨텍스트의 도메인 객체를 재사용하지 않는 것이
 * 컨텍스트 경계를 지키는 방법이다(읽기 모델은 그 컨텍스트의 불변식을 책임지지 않는다).
 */
public interface AdminReadModelPort {

    /** 컬렉션별 도큐먼트 수. (요구사항 2.2) */
    Map<String, Long> collectionCounts(List<String> collections);

    /** 주문 상태별 건수 + 완료 주문 거래액(GMV). (요구사항 2.2) */
    OrderStats orderStats();

    /** ACTIVE 매물 수. (요구사항 2.2) */
    long activeListingCount();

    /** 최근 주문. status가 null이면 전체. (요구사항 7.1) */
    List<OrderRow> recentOrders(String status, int limit);

    /** 최근 매물 — 일반 검색과 달리 DELETED 포함 전체 상태. (요구사항 4.1) */
    List<ListingRow> recentListings(int limit);

    /** 최근 게시글 — 전체 상태. (요구사항 5.1) */
    List<PostRow> recentPosts(int limit);

    record OrderStats(Map<String, Long> countByStatus, long completedGmv) {}

    record OrderRow(
            String id,
            String status,
            long amount,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            Instant createdAt) {}

    record ListingRow(
            String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {}

    record PostRow(String id, String authorId, String content, String type, String status, Instant createdAt) {}
}
