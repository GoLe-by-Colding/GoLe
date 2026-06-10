package com.gole.api.pricing.adapter.out.persistence;

import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort;
import com.gole.api.pricing.application.port.out.PriceTransactionRepositoryPort.TradeAggregate;
import com.gole.api.pricing.domain.model.PriceTransaction;
import java.time.Instant;
import java.util.List;
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
 * <p>전체 기간 조회는 {@link PriceTransactionMongoRepository} 파생 쿼리로, 기간(from/to)
 * 필터가 있는 조회는 {@link MongoTemplate}으로 처리한다.
 */
@Component
public class PriceTransactionPersistenceAdapter implements PriceTransactionRepositoryPort {

    private final PriceTransactionMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public PriceTransactionPersistenceAdapter(
            PriceTransactionMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PriceTransaction save(PriceTransaction transaction) {
        PriceTransactionDocument saved = repository.save(toDocument(transaction));
        return toDomain(saved);
    }

    @Override
    public List<PriceTransaction> findInRangeAscending(
            String setNumber, Instant from, Instant to) {
        // from/to가 모두 없으면 단순 파생 쿼리로 처리.
        if (from == null && to == null) {
            return repository.findBySetNumberOrderByExecutedAtAsc(setNumber).stream()
                    .map(this::toDomain)
                    .toList();
        }

        Criteria criteria = Criteria.where("setNumber").is(setNumber);
        if (from != null && to != null) {
            criteria = criteria.and("executedAt").gte(from).lte(to);
        } else if (from != null) {
            criteria = criteria.and("executedAt").gte(from);
        } else {
            criteria = criteria.and("executedAt").lte(to);
        }

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.ASC, "executedAt"));
        return mongoTemplate.find(query, PriceTransactionDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PriceTransaction> findByConditionAscending(
            String setNumber, com.gole.api.pricing.domain.model.SetCondition condition) {
        return repository
                .findBySetNumberAndConditionOrderByExecutedAtAsc(setNumber, condition.key())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TradeAggregate> findTopTradedSets(int limit, java.time.Instant since) {
        java.util.List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> ops =
                new java.util.ArrayList<>();
        if (since != null) {
            ops.add(Aggregation.match(Criteria.where("executedAt").gte(since)));
        }
        ops.add(Aggregation.group("setNumber")
                .count().as("tradeCount")
                .avg("price").as("averagePrice"));
        ops.add(Aggregation.sort(Sort.Direction.DESC, "tradeCount"));
        ops.add(Aggregation.limit(limit));

        AggregationResults<TradeAggregateRow> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), "price_transactions", TradeAggregateRow.class);

        return results.getMappedResults().stream()
                .map(row -> new TradeAggregate(
                        row.id(), row.tradeCount(), Math.round(row.averagePrice())))
                .toList();
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
                transaction.condition().key());
    }

    private PriceTransaction toDomain(PriceTransactionDocument document) {
        return new PriceTransaction(
                document.getSetNumber(),
                document.getPrice(),
                document.getQuantity(),
                document.getExecutedAt(),
                com.gole.api.pricing.domain.model.SetCondition.fromKey(document.getCondition()));
    }
}
