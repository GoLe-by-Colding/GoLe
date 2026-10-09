package com.gole.api.bid.adapter.out.persistence;

import com.gole.api.bid.application.port.out.BidRepositoryPort;
import com.gole.api.bid.domain.exception.DuplicateActiveBidException;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.bid.domain.model.BidStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 입찰 영속성 어댑터. 상태 전이는 {@code findAndModify}에 읽은 원본(저장 상태·가격을 건 시각·만료 시각)을 조건으로
 * 걸어 원자적으로 한다 — 같은 입찰을 두 판매자가 동시에 채우면 한쪽만 이긴다. (buy-bids D7, B5)
 *
 * <p>시각은 저장 전에 밀리초로 자른다. Mongo가 밀리초까지만 저장하므로, 자르지 않으면 돌려준 도메인 객체와 저장된
 * 문서가 달라 다음 조건부 갱신이 엉뚱하게 실패한다({@code OfferPersistenceAdapter}와 같은 이유).
 */
@Component
public class BidPersistenceAdapter implements BidRepositoryPort {

    /** 호가창 한 번에 읽는 상한. 지금 규모에서는 닿지 않는다 — 넘기면 집계 쿼리로 바꾼다. */
    private static final int BOOK_SCAN_LIMIT = 5_000;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));
    private static final Sort BEST_FIRST =
            Sort.by(Sort.Order.desc("price"), Sort.Order.asc("placedAt"), Sort.Order.asc("_id"));

    private final MongoTemplate mongo;

    public BidPersistenceAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Bid insert(Bid bid) {
        try {
            return toDomain(mongo.insert(toDocument(bid)));
        } catch (DuplicateKeyException concurrentActive) {
            throw new DuplicateActiveBidException();
        }
    }

    @Override
    public Optional<Bid> findById(String bidId) {
        if (bidId == null || bidId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(bidId, BidDocument.class)).map(BidPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Bid> findActive(String bidderId, String setNumber, BidCondition condition, Instant now) {
        Query query = Query.query(active(now)
                .and("bidderId")
                .is(bidderId)
                .and("setNumber")
                .is(setNumber)
                .and("condition")
                .is(condition.key()));
        return Optional.ofNullable(mongo.findOne(query, BidDocument.class)).map(BidPersistenceAdapter::toDomain);
    }

    @Override
    public long expireStale(String bidderId, String setNumber, BidCondition condition, Instant now) {
        Query stale = Query.query(Criteria.where("bidderId")
                .is(bidderId)
                .and("setNumber")
                .is(setNumber)
                .and("condition")
                .is(condition.key())
                .and("status")
                .is(BidStatus.ACTIVE.name())
                .and("expiresAt")
                .lte(now));
        return mongo.updateMulti(stale, Update.update("status", BidStatus.EXPIRED.name()), BidDocument.class)
                .getModifiedCount();
    }

    @Override
    public long countActive(String bidderId, Instant now) {
        return mongo.count(Query.query(active(now).and("bidderId").is(bidderId)), BidDocument.class);
    }

    @Override
    public Optional<Bid> transition(Bid current, Bid next) {
        if (!current.id().equals(next.id())) {
            throw new IllegalArgumentException("다른 입찰로 전이할 수 없다");
        }
        Query unchanged = Query.query(Criteria.where("_id")
                .is(current.id())
                .and("status")
                .is(current.status().name())
                .and("placedAt")
                .is(millis(current.placedAt()))
                .and("expiresAt")
                .is(millis(current.expiresAt())));
        Update update = new Update()
                .set("status", next.status().name())
                .set("price", next.price())
                .set("durationDays", next.durationDays())
                .set("placedAt", millis(next.placedAt()))
                .set("expiresAt", millis(next.expiresAt()))
                .set("closedAt", millis(next.closedAt()))
                .set("filledListingId", next.filledListingId())
                .set("offerId", next.offerId());
        BidDocument updated = mongo.findAndModify(
                unchanged, update, FindAndModifyOptions.options().returnNew(true), BidDocument.class);
        return Optional.ofNullable(updated).map(BidPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Bid> replaceIfActive(Bid replaced, Instant now) {
        Query stillActive = Query.query(active(now).and("_id").is(replaced.id()));
        Update update = new Update()
                .set("price", replaced.price())
                .set("durationDays", replaced.durationDays())
                .set("placedAt", millis(replaced.placedAt()))
                .set("expiresAt", millis(replaced.expiresAt()));
        BidDocument updated = mongo.findAndModify(
                stillActive, update, FindAndModifyOptions.options().returnNew(true), BidDocument.class);
        return Optional.ofNullable(updated).map(BidPersistenceAdapter::toDomain);
    }

    @Override
    public List<Bid> findFilledForListing(String listingId) {
        Query query = Query.query(Criteria.where("filledListingId")
                        .is(listingId)
                        .and("status")
                        .is(BidStatus.FILLED.name()))
                .with(NEWEST_FIRST);
        return mongo.find(query, BidDocument.class).stream()
                .map(BidPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Bid> findByBidder(String bidderId, int limit) {
        Query query = Query.query(Criteria.where("bidderId").is(bidderId))
                .with(NEWEST_FIRST)
                .limit(Math.max(1, limit));
        return mongo.find(query, BidDocument.class).stream()
                .map(BidPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Bid> findActiveBySet(String setNumber, Instant now) {
        Query query = Query.query(active(now).and("setNumber").is(setNumber))
                .with(BEST_FIRST)
                .limit(BOOK_SCAN_LIMIT);
        return mongo.find(query, BidDocument.class).stream()
                .map(BidPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Bid> findFillCandidates(
            String setNumber, BidCondition condition, String excludeBidderId, Instant now, int limit) {
        Query query = Query.query(active(now)
                        .and("setNumber")
                        .is(setNumber)
                        .and("condition")
                        .is(condition.key())
                        .and("bidderId")
                        .ne(excludeBidderId))
                .with(BEST_FIRST)
                .limit(Math.max(1, limit));
        return mongo.find(query, BidDocument.class).stream()
                .map(BidPersistenceAdapter::toDomain)
                .toList();
    }

    /** 사용자·세트·상태당 저장 {@code ACTIVE}가 하나라서 입찰자가 겹치지 않는다. */
    @Override
    public List<String> findBiddersAtOrAbove(
            String setNumber, BidCondition condition, long price, Instant now, int limit) {
        Query query = Query.query(active(now)
                        .and("setNumber")
                        .is(setNumber)
                        .and("condition")
                        .is(condition.key())
                        .and("price")
                        .gte(price))
                .with(BEST_FIRST)
                .limit(Math.max(1, limit));
        query.fields().include("bidderId");
        return mongo.find(query, BidDocument.class).stream()
                .map(BidDocument::getBidderId)
                .toList();
    }

    private static Criteria active(Instant now) {
        return Criteria.where("status")
                .is(BidStatus.ACTIVE.name())
                .and("expiresAt")
                .gt(now);
    }

    private static Instant millis(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    private static BidDocument toDocument(Bid bid) {
        return new BidDocument(
                bid.id(),
                bid.bidderId(),
                bid.setNumber(),
                bid.condition().key(),
                bid.price(),
                bid.durationDays(),
                bid.status().name(),
                millis(bid.createdAt()),
                millis(bid.placedAt()),
                millis(bid.expiresAt()),
                millis(bid.closedAt()),
                bid.filledListingId(),
                bid.offerId());
    }

    private static Bid toDomain(BidDocument document) {
        return new Bid(
                document.getId(),
                document.getBidderId(),
                document.getSetNumber(),
                BidCondition.fromKey(document.getCondition())
                        .orElseThrow(() -> new IllegalStateException("알 수 없는 입찰 상태 키: " + document.getCondition())),
                document.getPrice(),
                document.getDurationDays(),
                BidStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                document.getPlacedAt() == null ? document.getCreatedAt() : document.getPlacedAt(),
                document.getExpiresAt(),
                document.getClosedAt(),
                document.getFilledListingId(),
                document.getOfferId());
    }
}
