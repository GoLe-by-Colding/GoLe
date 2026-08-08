package com.gole.api.account.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 계정 Spring Data MongoDB 리포지토리.
 */
public interface AccountMongoRepository extends MongoRepository<AccountDocument, String> {

    boolean existsByEmail(String email);

    Optional<AccountDocument> findByEmail(String email);

    /** 운영 화면용 목록(정렬/건수는 Pageable로 전달). */
    List<AccountDocument> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    List<AccountDocument> findBy(Pageable pageable);

    long countByRole(String role);
}
