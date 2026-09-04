package com.gole.api.chat.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportConversationRetentionHoldMongoRepository
        extends MongoRepository<SupportConversationRetentionHoldDocument, String> {}
