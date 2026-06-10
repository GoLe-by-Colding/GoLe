package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageMongoRepository extends MongoRepository<ChatMessageDocument, String> {

    List<ChatMessageDocument> findByRoomIdOrderBySentAtAsc(String roomId);
}
