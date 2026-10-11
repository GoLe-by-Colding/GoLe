package com.gole.api.listing.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * 끌올 도입 전 매물 문서에 정렬 키 {@code listedAt}을 채운다. (listing-edit-and-bump B6)
 *
 * <p>"최신순"이 {@code listedAt} 내림차순으로 바뀌었는데 기존 문서에는 그 필드가 없다. 없는 값은
 * 내림차순에서 맨 뒤로 가므로, 채우지 않으면 기존 매물이 새 매물·끌올 매물 밑으로 전부 가라앉는다.
 * 읽기 경로도 {@code createdAt}으로 대신 보지만 그건 응답 값일 뿐이고 DB 정렬은 저장값을 본다.
 *
 * <p>파이프라인 갱신({@code $set: {listedAt: "$createdAt"}})이라 문서마다 자기 등록 시각을 쓴다.
 * {@code listedAt}이 없는 문서만 고르므로 몇 번 돌아도 같은 결과다(멱등). 이미 끌올된 매물은 건드리지 않는다.
 */
@Component
public class ListingListedAtBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ListingListedAtBackfill.class);

    private final MongoTemplate mongo;

    public ListingListedAtBackfill(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        long filled = backfill();
        if (filled > 0) {
            log.info("[migration] listing listedAt backfilled from createdAt: count={}", filled);
        }
    }

    /** {@code listedAt}이 없거나 null인 문서를 {@code createdAt}으로 채우고 바꾼 문서 수를 돌려준다. */
    public long backfill() {
        Query legacy =
                Query.query(Criteria.where("listedAt").is(null).and("createdAt").exists(true));
        AggregationUpdate fromCreatedAt =
                AggregationUpdate.update().set("listedAt").toValueOf(Fields.field("createdAt"));
        return mongo.updateMulti(legacy, fromCreatedAt, ListingDocument.class).getModifiedCount();
    }
}
