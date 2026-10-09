package com.gole.api.parts.adapter.out.persistence;

import com.gole.api.parts.adapter.out.persistence.PartRequestDocument.ItemDocument;
import com.gole.api.parts.application.port.out.PartRequestRepositoryPort;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import com.gole.api.parts.domain.model.WantedPart;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** 부품 요청 영속성 어댑터. 도메인 {@link PartRequest}와 {@link PartRequestDocument}를 양방향 매핑한다. */
@Component
public class MongoPartRequestAdapter implements PartRequestRepositoryPort {

    /** 계정 삭제 파기({@code MongoAccountDeletionAdapter})도 이 이름으로 지운다. */
    public static final String COLLECTION = "part_requests";

    /** 같은 시각에 만든 요청끼리도 순서가 흔들리지 않도록 id를 보조 키로 쓴다. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    private final MongoTemplate mongo;

    public MongoPartRequestAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public PartRequest save(PartRequest request) {
        return toDomain(mongo.save(toDocument(request), COLLECTION));
    }

    @Override
    public Optional<PartRequest> findById(String requestId) {
        return Optional.ofNullable(mongo.findById(requestId, PartRequestDocument.class, COLLECTION))
                .map(MongoPartRequestAdapter::toDomain);
    }

    @Override
    public boolean saveClosureIfOpen(PartRequest closed) {
        Query query = Query.query(
                Criteria.where("_id").is(closed.getId()).and("status").is(PartRequestStatus.OPEN.name()));
        Update update = new Update().set("status", closed.getStatus().name()).set("closedAt", closed.getClosedAt());
        return mongo.updateFirst(query, update, PartRequestDocument.class, COLLECTION)
                        .getMatchedCount()
                > 0;
    }

    @Override
    public long countOpenByRequester(String requesterId) {
        Query query = Query.query(
                Criteria.where("requesterId").is(requesterId).and("status").is(PartRequestStatus.OPEN.name()));
        return mongo.count(query, PartRequestDocument.class, COLLECTION);
    }

    @Override
    public List<PartRequest> search(String setNumber, PartRequestStatus status, int limit) {
        Query query = new Query().with(NEWEST_FIRST).limit(limit);
        if (setNumber != null) {
            query.addCriteria(Criteria.where("setNumber").is(setNumber));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status.name()));
        }
        return find(query);
    }

    @Override
    public List<PartRequest> findByRequester(String requesterId, int limit) {
        return find(Query.query(Criteria.where("requesterId").is(requesterId))
                .with(NEWEST_FIRST)
                .limit(limit));
    }

    @Override
    public void deleteById(String requestId) {
        mongo.remove(Query.query(Criteria.where("_id").is(requestId)), PartRequestDocument.class, COLLECTION);
    }

    private List<PartRequest> find(Query query) {
        return mongo.find(query, PartRequestDocument.class, COLLECTION).stream()
                .map(MongoPartRequestAdapter::toDomain)
                .toList();
    }

    private static PartRequestDocument toDocument(PartRequest request) {
        return new PartRequestDocument(
                request.getId(),
                request.getRequesterId(),
                request.getSetNumber(),
                request.getItems().stream()
                        .map(item -> new ItemDocument(item.partNumber(), item.colorName(), item.quantity()))
                        .toList(),
                request.getNote(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getClosedAt());
    }

    private static PartRequest toDomain(PartRequestDocument document) {
        List<ItemDocument> items = document.items() == null ? List.of() : document.items();
        return PartRequest.restore(
                document.id(),
                document.requesterId(),
                document.setNumber(),
                items.stream()
                        .map(item -> new WantedPart(item.partNumber(), item.colorName(), item.quantity()))
                        .toList(),
                document.note(),
                PartRequestStatus.valueOf(document.status()),
                document.createdAt(),
                document.closedAt());
    }
}
