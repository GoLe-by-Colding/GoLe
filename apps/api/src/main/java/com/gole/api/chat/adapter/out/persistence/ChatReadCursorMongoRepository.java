package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatReadCursorMongoRepository extends MongoRepository<ChatReadCursorDocument, String> {

    List<ChatReadCursorDocument> findByAccountIdAndRoomIdIn(String accountId, List<String> roomIds);
}
