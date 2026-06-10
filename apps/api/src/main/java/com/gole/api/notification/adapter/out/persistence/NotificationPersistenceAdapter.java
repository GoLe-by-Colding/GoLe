package com.gole.api.notification.adapter.out.persistence;

import com.gole.api.notification.application.port.out.NotificationRepositoryPort;
import com.gole.api.notification.domain.model.Notification;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 알림 영속성 어댑터. 도메인 {@link Notification}과 {@link NotificationDocument}를 매핑한다.
 * 전체 읽음 처리는 {@link MongoTemplate} 다건 갱신으로 효율 처리한다.
 */
@Component
public class NotificationPersistenceAdapter implements NotificationRepositoryPort {

    private final NotificationMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public NotificationPersistenceAdapter(NotificationMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Notification save(Notification notification) {
        return toDomain(repository.save(toDocument(notification)));
    }

    @Override
    public List<Notification> findByRecipientNewestFirst(String recipientId) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countUnread(String recipientId) {
        return repository.countByRecipientIdAndReadIsFalse(recipientId);
    }

    @Override
    public Optional<Notification> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public void markAllRead(String recipientId) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("recipientId")
                        .is(recipientId)
                        .and("read")
                        .is(false)),
                new Update().set("read", true),
                NotificationDocument.class);
    }

    private NotificationDocument toDocument(Notification n) {
        return new NotificationDocument(
                n.getId(),
                n.getRecipientId(),
                n.getType().name(),
                n.getMessage(),
                n.getLink(),
                n.isRead(),
                n.getCreatedAt());
    }

    private Notification toDomain(NotificationDocument d) {
        return new Notification(
                d.getId(),
                d.getRecipientId(),
                NotificationType.valueOf(d.getType()),
                d.getMessage(),
                d.getLink(),
                d.isRead(),
                d.getCreatedAt());
    }
}
