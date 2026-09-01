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

    /** 닉네임 유일성 확인. 비교는 정규화(소문자) 필드로 한다. (onboarding R3, D9) */
    Optional<AccountDocument> findByNicknameNormalized(String nicknameNormalized);

    /** 인증까지 끝난 같은 번호의 계정. 미인증 입력은 점유로 치지 않는다. (onboarding R4, D4) */
    Optional<AccountDocument> findByPhoneNumberAndPhoneVerifiedAtNotNull(String phoneNumber);
}
