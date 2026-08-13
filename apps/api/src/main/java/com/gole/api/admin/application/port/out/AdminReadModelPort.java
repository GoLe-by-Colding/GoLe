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

    /** 주문 상태별 건수 + 완료 주문 거래액(GMV) + 플랫폼 수수료 수익. (요구사항 2.2) */
    OrderStats orderStats();

    /** ACTIVE 매물 수. (요구사항 2.2) */
    long activeListingCount();

    /** 최근 주문. status가 null이면 전체. (요구사항 7.1) */
    List<OrderRow> recentOrders(String status, int limit);

    /** 최근 매물 — 일반 검색과 달리 DELETED 포함 전체 상태. (요구사항 4.1) */
    List<ListingRow> recentListings(int limit);

    /** 최근 게시글 — 전체 상태. (요구사항 5.1) */
    List<PostRow> recentPosts(int limit);

    /**
     * @param completedGmv    완료 주문 거래액 합계
     * @param platformRevenue 완료 주문의 플랫폼 수수료 합계. GMV가 아니라 <b>이게 매출</b>이다.
     */
    record OrderStats(Map<String, Long> countByStatus, long completedGmv, long platformRevenue) {}

    /**
     * 정산 3개 값(fee/payout/feeRate)은 완료·정산된 주문에만 있어 nullable이다. 0으로 채우면
     * "수수료가 0원인 주문"과 "아직 정산되지 않은 주문"이 화면에서 구분되지 않는다.
     *
     * @param feeRate 정산에 실제로 적용된 요율. 현재 설정값이 아니라 정산 시점의 값이다.
     */
    record OrderRow(
            String id,
            String status,
            long amount,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            Long fee,
            Long payout,
            Double feeRate,
            Instant createdAt) {}

    record ListingRow(
            String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {}

    record PostRow(String id, String authorId, String content, String type, String status, Instant createdAt) {}
}
