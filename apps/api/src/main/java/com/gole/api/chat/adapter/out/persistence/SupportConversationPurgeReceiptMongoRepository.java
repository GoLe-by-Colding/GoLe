package com.gole.api.chat.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportConversationPurgeReceiptMongoRepository
        extends MongoRepository<SupportConversationPurgeReceiptDocument, String> {

    Optional<SupportConversationPurgeReceiptDocument> findByIdempotencyKeyHash(String idempotencyKeyHash);
}
