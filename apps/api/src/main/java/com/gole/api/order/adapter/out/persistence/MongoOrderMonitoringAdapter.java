package com.gole.api.order.adapter.out.persistence;

import com.gole.api.order.application.port.in.MonitorOrdersUseCase.OrderMonitorRow;
import com.gole.api.order.application.port.in.MonitorOrdersUseCase.OrderStats;
import com.gole.api.order.application.port.out.OrderMonitoringPort;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 운영 화면용 주문 집계·목록. 도메인 매핑을 거치지 않고 원본 문서를 읽는다 — 오래된 문서의 모르는 상태나 빠진
 * 필드가 있어도 운영 화면은 그대로 보여 줘야 하기 때문이다.
 */
@Component
public class MongoOrderMonitoringAdapter implements OrderMonitoringPort {

    private static final String COLLECTION = "orders";

    private final MongoTemplate mongoTemplate;

    public MongoOrderMonitoringAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public OrderStats orderStats() {
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        long completedGmv = 0L;
        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(Aggregation.group("status")
                        .count()
                        .as("count")
                        .sum("amount")
                        .as("sum")),
                COLLECTION,
                Document.class);
        for (Document row : results) {
            String status = row.getString("_id");
            countByStatus.put(status, toLong(row.get("count")));
            if ("COMPLETED".equals(status)) {
                completedGmv += toLong(row.get("sum"));
            }
        }
        return new OrderStats(countByStatus, completedGmv);
    }

    @Override
    public List<OrderMonitorRow> recentOrders(String status, String search, int limit) {
        Query query = new Query();
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (search != null && !search.isBlank()) {
            Pattern term = contains(search);
            query.addCriteria(new Criteria()
                    .orOperator(
                            Criteria.where("_id").regex(term),
                            Criteria.where("buyerId").regex(term),
                            Criteria.where("sellerId").regex(term),
                            Criteria.where("catalogSetNumber").regex(term)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, COLLECTION).stream()
                .map(MongoOrderMonitoringAdapter::row)
                .toList();
    }

    @Override
    public long estimatedOrderCount() {
        return mongoTemplate.getCollection(COLLECTION).estimatedDocumentCount();
    }

    /**
     * 임베디드 결제수단은 결제 전 주문에는 아예 없다. {@code type}이 없는 결제수단은 행 전체를 비운다 — 사업자만 있고
     * 분류가 없는 값은 화면에서 "카카오페이"로 보이지만 무엇으로 결제됐는지는 말해 주지 못한다.
     */
    private static OrderMonitorRow row(Document d) {
        String methodType = null;
        String provider = null;
        if (d.get("paymentMethod") instanceof Document method) {
            String type = method.getString("type");
            if (type != null && !type.isBlank()) {
                methodType = type;
                provider = method.getString("provider");
            }
        }
        return new OrderMonitorRow(
                str(d.get("_id")),
                d.getString("status"),
                toLong(d.get("amount")),
                d.getString("buyerId"),
                d.getString("sellerId"),
                d.getString("catalogSetNumber"),
                methodType,
                provider,
                instant(d.get("createdAt")));
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static Pattern contains(String value) {
        return Pattern.compile(Pattern.quote(value.trim()), Pattern.CASE_INSENSITIVE);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }
}
