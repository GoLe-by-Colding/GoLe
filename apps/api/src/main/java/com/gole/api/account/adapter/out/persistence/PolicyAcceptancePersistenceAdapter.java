package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.PolicyAcceptanceRepositoryPort;
import com.gole.api.account.domain.model.PolicyAcceptance;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Mongo 정책 확인 이력 어댑터. 재시도나 OAuth 동시 콜백에도 같은 버전은 한 행만 남긴다. */
@Component
public class PolicyAcceptancePersistenceAdapter implements PolicyAcceptanceRepositoryPort {

    private final PolicyAcceptanceMongoRepository repository;

    public PolicyAcceptancePersistenceAdapter(PolicyAcceptanceMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void appendOnce(PolicyAcceptance acceptance) {
        try {
            repository.save(new PolicyAcceptanceDocument(
                    acceptance.id(),
                    acceptance.accountId(),
                    acceptance.termsVersion(),
                    acceptance.privacyVersion(),
                    acceptance.termsAccepted(),
                    acceptance.privacyAcknowledged(),
                    acceptance.minimumAgeConfirmed(),
                    acceptance.channel().name(),
                    acceptance.acceptedAt()));
        } catch (DuplicateKeyException alreadyRecorded) {
            // 동일 계정·동일 문서 버전의 재시도는 멱등 성공이다. 기존 감사 행은 덮어쓰지 않는다.
        }
    }
}
