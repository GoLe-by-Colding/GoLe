package com.gole.api.discovery.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 팔로우 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface FollowMongoRepository extends MongoRepository<FollowDocument, String> {

    boolean existsByUserIdAndSellerId(String userId, String sellerId);

    void deleteByUserIdAndSellerId(String userId, String sellerId);

    List<FollowDocument> findByUserId(String userId);

    List<FollowDocument> findBySellerId(String sellerId);

    long countBySellerId(String sellerId);
}
