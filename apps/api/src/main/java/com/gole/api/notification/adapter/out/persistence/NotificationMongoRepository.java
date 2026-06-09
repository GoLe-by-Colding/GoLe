package com.gole.api.notification.adapter.out.persistence;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 알림 Spring Data MongoDB 리포지토리.
 */
public interface NotificationMongoRepository
        extends MongoRepository<NotificationDocument, String> {

    List<NotificationDocument> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndReadIsFalse(String recipientId);
}
