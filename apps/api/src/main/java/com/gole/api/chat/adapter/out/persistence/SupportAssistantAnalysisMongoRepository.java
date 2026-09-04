package com.gole.api.chat.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportAssistantAnalysisMongoRepository
        extends MongoRepository<SupportAssistantAnalysisDocument, String> {}
