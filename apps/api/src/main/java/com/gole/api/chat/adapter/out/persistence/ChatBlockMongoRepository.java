package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatBlockMongoRepository extends MongoRepository<ChatBlockDocument, String> {

    List<ChatBlockDocument> findAllByBlockerId(String blockerId);
}
