package com.gole.api.account.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountDeletionRequestMongoRepository extends MongoRepository<AccountDeletionRequestDocument, String> {

    Optional<AccountDeletionRequestDocument> findByAccountId(String accountId);
}
