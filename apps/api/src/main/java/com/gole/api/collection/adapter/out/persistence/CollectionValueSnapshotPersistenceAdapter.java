package com.gole.api.collection.adapter.out.persistence;

import com.gole.api.collection.application.port.out.CollectionValueSnapshotRepositoryPort;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 컬렉션 가치 스냅샷 영속성 어댑터. 사용자·날짜 기준 upsert와 날짜 범위 조회. */
@Component
public class CollectionValueSnapshotPersistenceAdapter implements CollectionValueSnapshotRepositoryPort {

    private final MongoTemplate mongoTemplate;

    public CollectionValueSnapshotPersistenceAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void upsert(CollectionValueSnapshot snapshot) {
        Query key = Query.query(Criteria.where("userId")
                .is(snapshot.userId())
                .and("date")
                .is(snapshot.date().toString()));
        Update update = new Update()
                .set("ownedValue", snapshot.ownedValue())
                .set("ownedCount", snapshot.ownedCount())
                .set("pricedCount", snapshot.pricedCount())
                .set("capturedAt", snapshot.capturedAt());
        try {
            mongoTemplate.upsert(key, update, CollectionValueSnapshotDocument.class);
        } catch (DuplicateKeyException concurrentInsert) {
            // 스케줄러와 조회 시 갱신(H4)이 같은 사용자·날짜를 동시에 처음 넣으면 한쪽이 유일 인덱스에 걸린다.
            // 그때는 이미 문서가 있으므로 한 번 더 부르면 갱신으로 끝난다.
            mongoTemplate.upsert(key, update, CollectionValueSnapshotDocument.class);
        }
    }

    @Override
    public List<CollectionValueSnapshot> findRange(String userId, LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where("userId")
                        .is(userId)
                        .and("date")
                        .gte(from.toString())
                        .lte(to.toString()))
                .with(Sort.by(Sort.Direction.ASC, "date"));
        return mongoTemplate.find(query, CollectionValueSnapshotDocument.class).stream()
                .map(CollectionValueSnapshotPersistenceAdapter::toDomain)
                .toList();
    }

    private static CollectionValueSnapshot toDomain(CollectionValueSnapshotDocument document) {
        return new CollectionValueSnapshot(
                document.getUserId(),
                LocalDate.parse(document.getDate()),
                document.getOwnedValue(),
                document.getOwnedCount(),
                document.getPricedCount(),
                document.getCapturedAt());
    }
}
