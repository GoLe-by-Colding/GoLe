package com.gole.api.discovery.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 위시리스트 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface WishlistMongoRepository extends MongoRepository<WishlistEntryDocument, String> {

    boolean existsByUserIdAndTargetTypeAndTargetId(
            String userId, String targetType, String targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(
            String userId, String targetType, String targetId);

    List<WishlistEntryDocument> findByUserId(String userId);
}
