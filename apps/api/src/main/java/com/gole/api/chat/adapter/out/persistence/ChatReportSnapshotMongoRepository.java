package com.gole.api.chat.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatReportSnapshotMongoRepository extends MongoRepository<ChatReportSnapshotDocument, String> {

    Optional<ChatReportSnapshotDocument> findByReportId(String reportId);

    boolean existsByRoomId(String roomId);
}
