package com.gole.api.pricing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 체결 거래 Spring Data MongoDB 리포지토리.
 *
 * <p>전체 기간 조회는 파생 쿼리로 처리하고, 기간(from/to) 필터가 있는 조회는
 * {@link PriceTransactionPersistenceAdapter}가 {@code MongoTemplate}으로 직접 구성한다.
 */
public interface PriceTransactionMongoRepository
        extends MongoRepository<PriceTransactionDocument, String> {

    /** 특정 세트의 전체 체결 내역을 체결 시각 오름차순으로 조회. */
    List<PriceTransactionDocument> findBySetNumberOrderByExecutedAtAsc(String setNumber);
}
