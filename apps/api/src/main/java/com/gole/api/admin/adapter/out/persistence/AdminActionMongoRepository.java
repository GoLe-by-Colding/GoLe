package com.gole.api.admin.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 감사 로그 Spring Data MongoDB 리포지토리.
 */
public interface AdminActionMongoRepository extends MongoRepository<AdminActionDocument, String> {

    List<AdminActionDocument> findBy(Pageable pageable);
}
