package com.gole.api.chat.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SupportTicketMongoRepository extends MongoRepository<SupportTicketDocument, String> {

    List<SupportTicketDocument> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    List<SupportTicketDocument> findByCategory(String category, Pageable pageable);

    List<SupportTicketDocument> findByStatusAndCategory(String status, String category, Pageable pageable);

    @Query("{ 'category': { $in: [ null, 'GENERAL' ] } }")
    List<SupportTicketDocument> findGeneral(Pageable pageable);

    @Query("{ 'status': ?0, 'category': { $in: [ null, 'GENERAL' ] } }")
    List<SupportTicketDocument> findGeneralByStatus(String status, Pageable pageable);

    List<SupportTicketDocument> findBy(Pageable pageable);

    List<SupportTicketDocument> findByStatusNot(String status, Pageable pageable);

    @Query("{ $or: [ { 'requesterId': ?0 }, { 'assigneeId': ?0 } ] }")
    List<SupportTicketDocument> findByParticipant(String accountId, Pageable pageable);
}
