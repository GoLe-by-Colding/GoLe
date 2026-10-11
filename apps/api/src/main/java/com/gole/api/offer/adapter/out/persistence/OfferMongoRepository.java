package com.gole.api.offer.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/** 단건 조회용 저장소. 조건부 갱신·목록 조회는 {@link OfferPersistenceAdapter}가 {@code MongoTemplate}으로 한다. */
public interface OfferMongoRepository extends MongoRepository<OfferDocument, String> {}
