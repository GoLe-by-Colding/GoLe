package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SupportTicketMongoRepository extends MongoRepository<SupportTicketDocument, String> {

    List<SupportTicketDocument> findByStatus(String status, Pageable pageable);

    List<SupportTicketDocument> findBy(Pageable pageable);

    @Query("{ $or: [ { 'requesterId': ?0 }, { 'assigneeId': ?0 } ] }")
    List<SupportTicketDocument> findByParticipant(String accountId, Pageable pageable);
}
