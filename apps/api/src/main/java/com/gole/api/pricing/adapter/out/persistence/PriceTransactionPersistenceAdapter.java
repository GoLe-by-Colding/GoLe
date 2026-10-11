package com.gole.api.pricing.adapter.out.persistence;

import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 체결 거래 영속성 어댑터. 도메인 {@link PriceTransaction}과 {@link PriceTransactionDocument}를
 * 양방향 매핑한다.
 *
 * <p>시계열 조회는 전부 {@link MongoTemplate} 한 경로로 모은다. 예전에는 전체 기간만 파생 쿼리로
 * 빠져 있었는데, 그러면 같은 질문에 정렬 계약이 둘이 되어 한쪽만 고치는 일이 생긴다.
 */
@Component
public class PriceTransactionPersistenceAdapter implements PriceTransactionRepositoryPort {

    /**
     * 시계열 조회의 정렬 계약. {@code executedAt} 만으로 정렬하면 **같은 시각 체결의 순서가
     * 호출마다 달라진다** — MongoDB 는 동점의 순서를 보장하지 않는다. 시드 데이터처럼 같은 주차에
     * 여러 건이 몰리거나 한 주문이 여러 건을 한꺼번에 남길 때 차트의 점 순서와 "최근 체결" 목록이
     * 새로고침마다 바뀐다. {@code _id} 를 깨기값으로 두어 전 구간에서 같은 답이 나오게 한다.
     */
    private static final Sort ASCENDING_BY_EXECUTION = Sort.by(Sort.Order.asc("executedAt"), Sort.Order.asc("_id"));

    private final PriceTransactionMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public PriceTransactionPersistenceAdapter(PriceTransactionMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PriceTransaction save(PriceTransaction transaction) {
        PriceTransactionDocument saved = repository.save(toDocument(transaction));
        return toDomain(saved);
    }

    @Override
    public List<PriceTransaction> findInRangeAscending(String setNumber, Instant from, Instant to) {
        Criteria criteria = Criteria.where("setNumber").is(setNumber);
        if (from != null && to != null) {
            criteria = criteria.and("executedAt").gte(from).lte(to);
        } else if (from != null) {
            criteria = criteria.and("executedAt").gte(from);
        } else if (to != null) {
            criteria = criteria.and("executedAt").lte(to);
        }

        return find(new Query(criteria));
    }

    @Override
    public List<PriceTransaction> findByConditionAscending(
            String setNumber, com.gole.api.pricing.domain.model.SetCondition condition) {
        // 현재 키만으로 조회하면 3단계 시절 체결(used_complete 등)이 빠진다. storageKeys()로 함께 훑는다.
        return findByStorageKeys(setNumber, condition.storageKeys());
    }

    @Override
    public List<PriceTransaction> findByConditionsAscending(
            String setNumber, List<com.gole.api.pricing.domain.model.SetCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<String> keys = conditions.stream()
                .flatMap(c -> c.storageKeys().stream())
                .distinct()
                .toList();
        return findByStorageKeys(setNumber, keys);
    }

    private List<PriceTransaction> findByStorageKeys(String setNumber, List<String> keys) {
        Criteria conditionCriteria = Criteria.where("condition").in(keys);
        if (keys.contains(com.gole.api.pricing.domain.model.SetCondition.NEW_SEALED.key())) {
            conditionCriteria = new Criteria()
                    .orOperator(conditionCriteria, Criteria.where("condition").is(null));
        }
        return find(
                new Query(new Criteria().andOperator(Criteria.where("setNumber").is(setNumber), conditionCriteria)));
    }

    /** 시계열 조회의 단일 진입점. 정렬 계약을 여기 한 곳에서만 건다. */
    private List<PriceTransaction> find(Query query) {
        return mongoTemplate.find(query.with(ASCENDING_BY_EXECUTION), PriceTransactionDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TradeAggregate> findTopTradedSets(
            int limit, java.time.Instant since, Set<PriceTransactionSource> includedSources) {
        java.util.List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> ops =
                new java.util.ArrayList<>();
        ops.add(Aggregation.match(sourceCriteria(includedSources)));
        if (since != null) {
            ops.add(Aggregation.match(Criteria.where("executedAt").gte(since)));
        }
        ops.add(Aggregation.group("setNumber")
                .count()
                .as("tradeCount")
                .avg("price")
                .as("averagePrice"));
        // 체결 수가 같은 세트끼리는 순서가 정해지지 않아 인기 목록이 호출마다 뒤바뀐다.
        // 세트 번호(_id)를 깨기값으로 두어 같은 데이터면 같은 목록이 나오게 한다.
        ops.add(Aggregation.sort(Sort.by(Sort.Order.desc("tradeCount"), Sort.Order.asc("_id"))));
        ops.add(Aggregation.limit(limit));

        AggregationResults<TradeAggregateRow> results =
                mongoTemplate.aggregate(Aggregation.newAggregation(ops), "price_transactions", TradeAggregateRow.class);

        return results.getMappedResults().stream()
                .map(row -> new TradeAggregate(row.id(), row.tradeCount(), Math.round(row.averagePrice())))
                .toList();
    }

    private static Criteria sourceCriteria(Set<PriceTransactionSource> includedSources) {
        List<String> keys =
                includedSources.stream().map(PriceTransactionSource::key).toList();
        boolean includeLegacy = includedSources.contains(PriceTransactionSource.LEGACY_UNVERIFIED);
        if (includeLegacy && !keys.isEmpty()) {
            return new Criteria()
                    .orOperator(
                            Criteria.where("source").in(keys),
                            Criteria.where("source").is(null));
        }
        if (includeLegacy) {
            return Criteria.where("source").is(null);
        }
        if (keys.isEmpty()) {
            return Criteria.where("_id").exists(false);
        }
        return Criteria.where("source").in(keys);
    }

    /** 집계 결과 행 매핑용. {@code _id}에는 group 키(setNumber)가 담긴다. */
    private record TradeAggregateRow(String id, long tradeCount, double averagePrice) {}

    private PriceTransactionDocument toDocument(PriceTransaction transaction) {
        // id는 신규 저장 시 MongoDB가 생성하도록 null로 둔다.
        return new PriceTransactionDocument(
                null,
                transaction.setNumber(),
                transaction.price(),
                transaction.quantity(),
                transaction.executedAt(),
                transaction.condition().key(),
                transaction.source().key(),
                transaction.sourceReference());
    }

    private PriceTransaction toDomain(PriceTransactionDocument document) {
        return new PriceTransaction(
                document.getSetNumber(),
                document.getPrice(),
                document.getQuantity(),
                document.getExecutedAt(),
                com.gole.api.pricing.domain.model.SetCondition.fromKey(document.getCondition()),
                PriceTransactionSource.fromKey(document.getSource()),
                document.getSourceReference());
    }
}
