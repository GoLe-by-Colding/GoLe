package com.gole.api.notification.adapter.out.persistence;

import com.gole.api.notification.application.port.out.NotificationPreferenceRepositoryPort;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** 알림 수신 설정 영속성 어댑터. */
@Component
public class NotificationPreferencePersistenceAdapter implements NotificationPreferenceRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferencePersistenceAdapter.class);

    private final NotificationPreferenceMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public NotificationPreferencePersistenceAdapter(
            NotificationPreferenceMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<NotificationPreferences> find(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(accountId).map(this::toDomain);
    }

    @Override
    public NotificationPreferences save(NotificationPreferences preferences) {
        // 계정 id가 _id이므로 save가 곧 upsert다.
        return toDomain(repository.save(new NotificationPreferenceDocument(
                preferences.getAccountId(),
                preferences.getDisabled().stream().map(Enum::name).sorted().toList(),
                preferences.getUpdatedAt())));
    }

    @Override
    public NotificationPreferences update(String accountId, Map<NotificationCategory, Boolean> changes, Instant now) {
        List<String> enabled = changes.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(entry -> entry.getKey().name())
                .toList();
        List<String> disabled = changes.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(entry -> entry.getKey().name())
                .toList();
        Document current = new Document("$ifNull", List.of("$disabledCategories", List.of()));
        Document kept = new Document("$setDifference", List.of(current, enabled));
        Document next = new Document("$setUnion", List.of(kept, disabled));
        AggregationUpdate update = AggregationUpdate.from(List.of(context ->
                new Document("$set", new Document("disabledCategories", next).append("updatedAt", Date.from(now)))));
        Query query = Query.query(Criteria.where("_id").is(accountId));
        NotificationPreferenceDocument stored;
        try {
            stored = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    NotificationPreferenceDocument.class);
        } catch (DuplicateKeyException simultaneousFirstSave) {
            // 같은 계정의 첫 저장끼리 경합하면 먼저 만들어진 문서에 변경분을 다시 합친다.
            stored = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    NotificationPreferenceDocument.class);
        }
        return toDomain(Objects.requireNonNull(stored, "notification preferences update result"));
    }

    @Override
    public Set<String> findAccountsDisabling(NotificationCategory category, Collection<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Set.of();
        }
        // _id 인덱스로 좁힌 뒤 배열 원소 일치로 거른다. 본문은 필요 없으므로 _id만 받는다.
        Query query = Query.query(
                Criteria.where("_id").in(accountIds).and("disabledCategories").is(category.name()));
        query.fields().include("_id");
        return mongoTemplate.find(query, NotificationPreferenceDocument.class).stream()
                .map(NotificationPreferenceDocument::getAccountId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private NotificationPreferences toDomain(NotificationPreferenceDocument document) {
        EnumSet<NotificationCategory> disabled = EnumSet.noneOf(NotificationCategory.class);
        if (document.getDisabledCategories() != null) {
            for (String name : document.getDisabledCategories()) {
                try {
                    disabled.add(NotificationCategory.valueOf(name));
                } catch (IllegalArgumentException | NullPointerException unknown) {
                    // 분류가 열거형에서 사라진 경우. 설정 전체를 버리지 않고 그 값만 건너뛴다 — 나머지는 지킨다.
                    log.warn("알 수 없는 알림 분류 '{}' — 이 값은 건너뛴다 accountId={}", name, document.getAccountId());
                }
            }
        }
        return new NotificationPreferences(document.getAccountId(), disabled, document.getUpdatedAt());
    }
}
