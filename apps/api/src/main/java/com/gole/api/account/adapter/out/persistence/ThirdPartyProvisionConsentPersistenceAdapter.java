package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.ThirdPartyProvisionConsentRepositoryPort;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.Decision;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Mongo 어댑터. insert만 사용해 기존 동의 감사 행을 덮어쓰지 않는다. */
@Component
public class ThirdPartyProvisionConsentPersistenceAdapter implements ThirdPartyProvisionConsentRepositoryPort {

    private final ThirdPartyProvisionConsentMongoRepository repository;

    public ThirdPartyProvisionConsentPersistenceAdapter(ThirdPartyProvisionConsentMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public ThirdPartyProvisionConsentEvent appendOnce(ThirdPartyProvisionConsentEvent event) {
        try {
            return toDomain(repository.insert(toDocument(event)));
        } catch (DuplicateKeyException duplicateRequest) {
            return repository
                    .findByAccountIdAndRequestId(event.accountId(), event.requestId())
                    .map(ThirdPartyProvisionConsentPersistenceAdapter::toDomain)
                    .orElseThrow(() -> duplicateRequest);
        }
    }

    @Override
    public Optional<ThirdPartyProvisionConsentEvent> findLatest(String accountId, String noticeVersion) {
        return repository
                .findFirstByAccountIdAndNoticeVersionOrderByOccurredAtDescIdDesc(accountId, noticeVersion)
                .map(ThirdPartyProvisionConsentPersistenceAdapter::toDomain);
    }

    private static ThirdPartyProvisionConsentDocument toDocument(ThirdPartyProvisionConsentEvent event) {
        return new ThirdPartyProvisionConsentDocument(
                event.id(),
                event.accountId(),
                event.noticeVersion(),
                event.decision().name(),
                event.sourcePath().name(),
                event.requestId(),
                event.occurredAt());
    }

    private static ThirdPartyProvisionConsentEvent toDomain(ThirdPartyProvisionConsentDocument document) {
        return new ThirdPartyProvisionConsentEvent(
                document.getId(),
                document.getAccountId(),
                document.getNoticeVersion(),
                Decision.valueOf(document.getDecision()),
                SourcePath.valueOf(document.getSourcePath()),
                document.getRequestId(),
                document.getOccurredAt());
    }
}
