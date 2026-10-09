package com.gole.api.listing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 리스팅 Spring Data MongoDB 리포지토리.
 *
 * <p>단순 조회는 파생 쿼리로 처리하고, 복합 검색 필터/정렬({@code ListingSearchQuery})은
 * {@link ListingPersistenceAdapter}가 {@code MongoTemplate}으로 직접 구성한다.
 */
public interface ListingMongoRepository extends MongoRepository<ListingDocument, String> {

    // "최신순"은 모두 listedAt(등록 또는 마지막 끌올) 내림차순이다. 셀러샵·내 매물·팔로우 피드가
    // 검색과 다른 키로 정렬하면 끌올한 매물이 화면마다 다른 자리에 선다. (listing-edit-and-bump B5)

    /** 특정 셀러의 특정 상태 리스팅(최신순). 셀러샵용. */
    List<ListingDocument> findBySellerIdAndStatusOrderByListedAtDesc(String sellerId, String status);

    /** 특정 셀러의 리스팅 중 특정 상태를 제외한 것(최신순). 본인 "내 매물" 조회용. */
    List<ListingDocument> findBySellerIdAndStatusNotOrderByListedAtDesc(String sellerId, String status);

    /** 여러 셀러의 특정 상태 리스팅을 최신순·제한 조회. */
    List<ListingDocument> findBySellerIdInAndStatusOrderByListedAtDesc(
            List<String> sellerIds, String status, Pageable pageable);

    /** id 목록으로 조회. */
    List<ListingDocument> findByIdIn(List<String> ids);
}
