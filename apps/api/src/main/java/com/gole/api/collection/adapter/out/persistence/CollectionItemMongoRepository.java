package com.gole.api.collection.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 컬렉션 항목 Spring Data MongoDB 리포지토리. 단순 조회는 파생 쿼리로 처리한다.
 */
public interface CollectionItemMongoRepository
        extends MongoRepository<CollectionItemDocument, String> {

    /** 특정 사용자의 컬렉션 항목 전체. */
    List<CollectionItemDocument> findByUserId(String userId);
}
