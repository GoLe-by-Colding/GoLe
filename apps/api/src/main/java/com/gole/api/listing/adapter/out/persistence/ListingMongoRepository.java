package com.gole.api.listing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 리스팅 Spring Data MongoDB 리포지토리.
 *
 * <p>단순 조회는 파생 쿼리로 처리하고, 복합 검색 필터/정렬({@code ListingSearchQuery})은
 * {@link ListingPersistenceAdapter}가 {@code MongoTemplate}으로 직접 구성한다.
 */
public interface ListingMongoRepository extends MongoRepository<ListingDocument, String> {

    /** 특정 셀러의 특정 상태 리스팅. */
    List<ListingDocument> findBySellerIdAndStatus(String sellerId, String status);

    /** 여러 셀러의 특정 상태 리스팅. */
    List<ListingDocument> findBySellerIdInAndStatus(List<String> sellerIds, String status);

    /** id 목록으로 조회. */
    List<ListingDocument> findByIdIn(List<String> ids);
}
