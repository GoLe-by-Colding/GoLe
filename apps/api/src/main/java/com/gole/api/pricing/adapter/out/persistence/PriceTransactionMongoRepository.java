package com.gole.api.pricing.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 체결 거래 Spring Data MongoDB 리포지토리.
 *
 * <p>쓰기(저장·개수)만 여기를 쓴다. **시계열 조회 파생 쿼리를 다시 만들지 않는다** —
 * 조회는 {@link PriceTransactionPersistenceAdapter} 가 {@code MongoTemplate} 한 경로로 모으고
 * 정렬 계약({@code executedAt}, {@code _id})을 거기 한 곳에서만 건다. 파생 쿼리로 우회하면
 * 같은 질문에 정렬이 둘이 되어 같은 시각 체결의 순서가 경로마다 달라진다.
 */
public interface PriceTransactionMongoRepository extends MongoRepository<PriceTransactionDocument, String> {}
