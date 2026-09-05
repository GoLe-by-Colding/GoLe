package com.gole.api.chat.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ChatMessageMongoRepository extends MongoRepository<ChatMessageDocument, String> {

    List<ChatMessageDocument> findTop60ByRoomIdOrderBySentAtDesc(String roomId);

    List<ChatMessageDocument> findByRoomId(String roomId, Pageable pageable);

    Optional<ChatMessageDocument> findFirstByRoomIdAndSenderIdOrderBySentAtAscIdAsc(String roomId, String senderId);

    @Query("{ 'roomId': ?0, '$or': [ { 'sentAt': { '$lt': ?1 } }, { 'sentAt': ?1, '_id': { '$lt': ?2 } } ] }")
    List<ChatMessageDocument> findContextBefore(String roomId, Instant sentAt, String messageId, Pageable pageable);

    @Query("{ 'roomId': ?0, '$or': [ { 'sentAt': { '$gt': ?1 } }, { 'sentAt': ?1, '_id': { '$gt': ?2 } } ] }")
    List<ChatMessageDocument> findContextAfter(String roomId, Instant sentAt, String messageId, Pageable pageable);
}
