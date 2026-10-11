package com.gole.api.community.adapter.out.persistence;

import com.gole.api.community.application.port.in.MonitorPostsUseCase.PostMonitorRow;
import com.gole.api.community.application.port.out.PostMonitoringPort;
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

/** 운영 화면용 게시글 목록. 모든 상태의 원본 문서를 읽는다. */
@Component
public class MongoPostMonitoringAdapter implements PostMonitoringPort {

    private static final String COLLECTION = "posts";

    private final MongoTemplate mongoTemplate;

    public MongoPostMonitoringAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<PostMonitorRow> recentPosts(String status, String search, int limit) {
        Query query = new Query();
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (search != null && !search.isBlank()) {
            Pattern term = Pattern.compile(Pattern.quote(search.trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria()
                    .orOperator(
                            Criteria.where("_id").regex(term),
                            Criteria.where("content").regex(term),
                            Criteria.where("authorId").regex(term),
                            Criteria.where("type").regex(term)));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, COLLECTION).stream()
                .map(d -> new PostMonitorRow(
                        d.get("_id") == null ? "" : d.get("_id").toString(),
                        d.getString("authorId"),
                        d.getString("content") == null ? "" : d.getString("content"),
                        d.getString("type"),
                        d.getString("status"),
                        instant(d.get("createdAt"))))
                .toList();
    }

    @Override
    public long estimatedPostCount() {
        return mongoTemplate.getCollection(COLLECTION).estimatedDocumentCount();
    }

    private static Instant instant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }
}
