package com.gole.api.account.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ThirdPartyProvisionConsentMongoRepository
        extends MongoRepository<ThirdPartyProvisionConsentDocument, String> {

    Optional<ThirdPartyProvisionConsentDocument> findByAccountIdAndRequestId(String accountId, String requestId);

    Optional<ThirdPartyProvisionConsentDocument> findFirstByAccountIdAndNoticeVersionOrderByOccurredAtDescIdDesc(
            String accountId, String noticeVersion);
}
