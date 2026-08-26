package com.gole.api.listing.application.port.out;

import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.model.Listing;
import java.util.List;
import java.util.Optional;

/**
 * 리스팅 영속성 outbound port. 도메인/애플리케이션은 저장 기술(MongoDB 등)에 의존하지 않는다.
 * 검색은 {@link ListingSearchQuery}의 필터/정렬을 그대로 위임받아 활성(ACTIVE) 리스팅만 반환한다.
 */
public interface ListingRepositoryPort {

    /** 리스팅을 저장(신규/갱신)하고 영속된 결과를 반환한다. */
    Listing save(Listing listing);

    /** id로 단건 조회. 없으면 비어있음. */
    Optional<Listing> findById(String listingId);

    /** 검색 조건/정렬에 따라 활성 리스팅을 조회한다. (요구사항 14) */
    List<Listing> search(ListingSearchQuery query);

    /**
     * 활성(ACTIVE) 리스팅을 원자적으로 RESERVED로 전이시키고 그 결과를 반환한다.
     * 활성 상태가 아니면 비어있음(선점 실패).
     */
    Optional<Listing> reserveIfActive(String listingId);

    /** 특정 셀러의 활성 리스팅 목록. (요구사항 16) */
    List<Listing> findActiveBySeller(String sellerId);

    /** 셀러의 리스팅(최신순, 삭제 제외). 본인 "내 매물" 조회용. */
    List<Listing> findBySeller(String sellerId);

    /** 여러 셀러의 활성 리스팅 목록(피드 구성 등). (요구사항 17) */
    List<Listing> findActiveBySellers(List<String> sellerIds);

    /** id 목록으로 리스팅을 조회한다(위시리스트 등). */
    List<Listing> findByIds(List<String> ids);
}
