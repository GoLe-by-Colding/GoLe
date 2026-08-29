package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SupportInternalNoteMongoRepository extends MongoRepository<SupportInternalNoteDocument, String> {

    List<SupportInternalNoteDocument> findByRoomId(String roomId, Pageable pageable);
}
