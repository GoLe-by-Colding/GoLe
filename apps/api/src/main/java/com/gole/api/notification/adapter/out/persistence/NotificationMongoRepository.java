package com.gole.api.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 알림 Spring Data MongoDB 리포지토리.
 */
public interface NotificationMongoRepository extends MongoRepository<NotificationDocument, String> {

    List<NotificationDocument> findTop100ByRecipientIdOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndReadIsFalse(String recipientId);

    Optional<NotificationDocument> findByRecipientIdAndDeduplicationKey(String recipientId, String deduplicationKey);
}
