package com.gole.api.listing.adapter.out.persistence;

import com.gole.api.listing.application.port.in.MonitorListingsUseCase.ListingMonitorRow;
import com.gole.api.listing.application.port.out.ListingMonitoringPort;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** 운영 화면용 매물 집계·목록. 모든 상태의 원본 문서를 읽는다. */
@Component
public class MongoListingMonitoringAdapter implements ListingMonitoringPort {

    private static final String COLLECTION = "listings";

    private final MongoTemplate mongoTemplate;

    public MongoListingMonitoringAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public long activeListingCount() {
        return mongoTemplate.getCollection(COLLECTION).countDocuments(new Document("status", "ACTIVE"));
    }

    @Override
    public List<ListingMonitorRow> recentListings(String status, String search, int limit) {
        Query query = new Query();
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (search != null && !search.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria()
                    .orOperator(
                            Criteria.where("_id").regex(term),
                            Criteria.where("title").regex(term),
                            Criteria.where("sellerId").regex(term),
                            Criteria.where("category").regex(term)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, COLLECTION).stream()
                .map(d -> new ListingMonitorRow(
                        d.get("_id") == null ? "" : d.get("_id").toString(),
                        d.getString("title"),
                        d.getString("sellerId"),
                        d.get("priceAmount") instanceof Number n ? n.longValue() : 0L,
                        d.getString("status"),
                        d.getString("category"),
                        instant(d.get("createdAt"))))
                .toList();
    }

    @Override
    public long estimatedListingCount() {
        return mongoTemplate.getCollection(COLLECTION).estimatedDocumentCount();
    }

    private static Instant instant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }
}
