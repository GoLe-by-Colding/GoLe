package com.gole.api.admin.adapter.out.readmodel;

import com.gole.api.admin.application.port.out.AdminReadModelPort;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 운영 리포팅 읽기 모델의 MongoDB 어댑터. (admin-console 요구사항 9.2)
 *
 * <p>이 클래스가 관리자 컨텍스트에서 유일하게 저장소를 아는 지점이다. 컨트롤러·서비스는
 * {@link AdminReadModelPort}만 알기 때문에, 나중에 리포팅을 별도 저장소나 뷰로 옮겨도 웹 계층은 그대로다.
 *
 * <p>도메인 리포지토리를 쓰지 않고 컬렉션을 직접 집계하는 이유: 운영 대시보드는 여러 컨텍스트를
 * 가로지르는 조회이고, 이를 위해 각 컨텍스트의 애그리거트를 전부 로드하는 것은 낭비이자
 * 컨텍스트 경계를 흐리는 일이다. 읽기 전용이므로 도메인 불변식을 건드리지도 않는다.
 */
@Component
public class MongoAdminReadModelAdapter implements AdminReadModelPort {

    private final MongoTemplate mongoTemplate;

    public MongoAdminReadModelAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Long> collectionCounts(List<String> collections) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String collection : collections) {
            counts.put(collection, mongoTemplate.getCollection(collection).estimatedDocumentCount());
        }
        return counts;
    }

    @Override
    public OrderStats orderStats() {
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        long completedGmv = 0L;
        long platformRevenue = 0L;
        AggregationResults<Document> results = mongoTemplate.aggregate(
                Aggregation.newAggregation(Aggregation.group("status")
                        .count()
                        .as("count")
                        .sum("amount")
                        .as("sum")
                        // 정산 전표가 없는 문서는 $sum이 0으로 취급한다(레거시 완료 주문 안전).
                        .sum("settlement.fee")
                        .as("fee")),
                "orders",
                Document.class);
        for (Document row : results) {
            String status = row.getString("_id");
            countByStatus.put(status, toLong(row.get("count")));
            if ("COMPLETED".equals(status)) {
                completedGmv += toLong(row.get("sum"));
                platformRevenue += toLong(row.get("fee"));
            }
        }
        return new OrderStats(countByStatus, completedGmv, platformRevenue);
    }

    @Override
    public long activeListingCount() {
        return mongoTemplate.getCollection("listings").countDocuments(new Document("status", "ACTIVE"));
    }

    @Override
    public List<OrderRow> recentOrders(String status, int limit) {
        Query query = new Query();
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, "orders").stream()
                .map(d -> {
                    Document settlement = d.get("settlement", Document.class);
                    return new OrderRow(
                            str(d.get("_id")),
                            d.getString("status"),
                            toLong(d.get("amount")),
                            d.getString("buyerId"),
                            d.getString("sellerId"),
                            d.getString("catalogSetNumber"),
                            nullableLong(settlement, "fee"),
                            nullableLong(settlement, "payout"),
                            nullableDouble(settlement, "feeRate"),
                            instant(d.get("createdAt")));
                })
                .toList();
    }

    @Override
    public List<ListingRow> recentListings(int limit) {
        Query query =
                new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, "listings").stream()
                .map(d -> new ListingRow(
                        str(d.get("_id")),
                        d.getString("title"),
                        d.getString("sellerId"),
                        toLong(d.get("priceAmount")),
                        d.getString("status"),
                        d.getString("category"),
                        instant(d.get("createdAt"))))
                .toList();
    }

    @Override
    public List<PostRow> recentPosts(int limit) {
        Query query =
                new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, "posts").stream()
                .map(d -> new PostRow(
                        str(d.get("_id")),
                        d.getString("authorId"),
                        d.getString("content") == null ? "" : d.getString("content"),
                        d.getString("type"),
                        d.getString("status"),
                        instant(d.get("createdAt"))))
                .toList();
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /** 임베디드 정산 문서의 정수 필드. 문서·필드가 없으면 null(미정산)을 그대로 돌려준다. */
    private static Long nullableLong(Document embedded, String field) {
        if (embedded == null) {
            return null;
        }
        return embedded.get(field) instanceof Number n ? n.longValue() : null;
    }

    /**
     * 임베디드 정산 문서의 실수 필드. feeRate는 도입 이전 문서에 없을 수 있는데, 이때는
     * null로 두어 "정산은 됐지만 요율 기록이 없음"을 화면이 구분할 수 있게 한다.
     */
    private static Double nullableDouble(Document embedded, String field) {
        if (embedded == null) {
            return null;
        }
        return embedded.get(field) instanceof Number n ? n.doubleValue() : null;
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
