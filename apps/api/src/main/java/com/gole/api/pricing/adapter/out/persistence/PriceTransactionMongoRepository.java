package com.gole.api.pricing.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 체결 거래 Spring Data MongoDB 리포지토리.
 *
 * <p>전체 기간 조회는 파생 쿼리로 처리하고, 기간(from/to) 필터가 있는 조회는
 * {@link PriceTransactionPersistenceAdapter}가 {@code MongoTemplate}으로 직접 구성한다.
 */
public interface PriceTransactionMongoRepository extends MongoRepository<PriceTransactionDocument, String> {

    /** 특정 세트의 전체 체결 내역을 체결 시각 오름차순으로 조회. */
    List<PriceTransactionDocument> findBySetNumberOrderByExecutedAtAsc(String setNumber);

    /** 특정 세트·상태의 체결 내역을 체결 시각 오름차순으로 조회. */
    List<PriceTransactionDocument> findBySetNumberAndConditionOrderByExecutedAtAsc(String setNumber, String condition);

    /**
     * 특정 세트의 여러 상태 키를 한 번에 조회(체결 시각 오름차순).
     *
     * <p>등급 하나를 조회할 때도 레거시 키를 함께 넘겨야 과거 체결이 빠지지 않으므로,
     * 단건 조회도 이 {@code In} 쿼리를 쓴다.
     */
    List<PriceTransactionDocument> findBySetNumberAndConditionInOrderByExecutedAtAsc(
            String setNumber, List<String> conditions);
}
