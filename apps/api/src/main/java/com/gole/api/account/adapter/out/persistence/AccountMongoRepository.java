package com.gole.api.account.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 계정 Spring Data MongoDB 리포지토리.
 */
public interface AccountMongoRepository extends MongoRepository<AccountDocument, String> {

    boolean existsByEmail(String email);

    Optional<AccountDocument> findByEmail(String email);
}
