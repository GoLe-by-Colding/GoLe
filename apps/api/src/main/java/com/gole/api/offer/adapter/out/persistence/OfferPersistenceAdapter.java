package com.gole.api.offer.adapter.out.persistence;

import com.gole.api.offer.application.port.out.OfferRepositoryPort;
import com.gole.api.offer.domain.exception.OfferErrors;
import com.gole.api.offer.domain.model.OfferOrigin;
import com.gole.api.offer.domain.model.OfferStatus;
import com.gole.api.offer.domain.model.PriceOffer;
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
 * 제안 영속성 어댑터. 상태 전이는 {@code findAndModify}에 현재 상태를 조건으로 걸어 원자적으로 한다
 * (price-offer O11).
 *
 * <p>시각은 저장 전에 밀리초로 자른다. Mongo가 밀리초까지만 저장하므로, 자르지 않으면 돌려준 도메인 객체와
 * 저장된 문서의 {@code expiresAt}이 달라 다음 조건부 갱신이 엉뚱하게 실패할 수 있다.
 */
@Component
public class OfferPersistenceAdapter implements OfferRepositoryPort {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    private final OfferMongoRepository repository;
    private final MongoTemplate mongo;

    public OfferPersistenceAdapter(OfferMongoRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    @Override
    public PriceOffer insert(PriceOffer offer) {
        try {
            return toDomain(mongo.insert(toDocument(offer)));
        } catch (DuplicateKeyException concurrentPending) {
            // 존재 검사와 저장 사이의 경쟁은 부분 유일 인덱스만 막을 수 있다.
            throw OfferErrors.alreadyPending();
        }
    }

    @Override
    public Optional<PriceOffer> findById(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(offerId).map(OfferPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<PriceOffer> transition(PriceOffer current, PriceOffer next) {
        if (!current.id().equals(next.id())) {
            throw new IllegalArgumentException("다른 제안으로 전이할 수 없다");
        }
        Query unchanged = Query.query(Criteria.where("_id")
                .is(current.id())
                .and("status")
                .is(current.status().name())
                .and("expiresAt")
                .is(millis(current.expiresAt())));
        Update update = new Update()
                .set("status", next.status().name())
                .set("respondedAt", millis(next.respondedAt()))
                .set("expiresAt", millis(next.expiresAt()));
        OfferDocument updated = mongo.findAndModify(
                unchanged, update, FindAndModifyOptions.options().returnNew(true), OfferDocument.class);
        return Optional.ofNullable(updated).map(OfferPersistenceAdapter::toDomain);
    }

    @Override
    public long expireStalePending(String listingId, String buyerId, Instant now) {
        Query stale = Query.query(Criteria.where("listingId")
                .is(listingId)
                .and("buyerId")
                .is(buyerId)
                .and("status")
                .is(OfferStatus.PENDING.name())
                .and("expiresAt")
                .lte(now));
        return mongo.updateMulti(stale, Update.update("status", OfferStatus.EXPIRED.name()), OfferDocument.class)
                .getModifiedCount();
    }

    @Override
    public boolean existsValidPending(String listingId, String buyerId, Instant now) {
        Query valid = Query.query(Criteria.where("listingId")
                .is(listingId)
                .and("buyerId")
                .is(buyerId)
                .and("status")
                .is(OfferStatus.PENDING.name())
                .and("expiresAt")
                .gt(now));
        return mongo.exists(valid, OfferDocument.class);
    }

    @Override
    public List<Instant> createdTimesSince(String listingId, String buyerId, Instant since, int limit) {
        Query recent = Query.query(Criteria.where("listingId")
                        .is(listingId)
                        .and("buyerId")
                        .is(buyerId)
                        .and("createdAt")
                        .gt(since))
                .with(Sort.by(Sort.Order.asc("createdAt")))
                .limit(Math.max(1, limit));
        recent.fields().include("createdAt");
        return mongo.find(recent, OfferDocument.class).stream()
                .map(OfferDocument::getCreatedAt)
                .toList();
    }

    @Override
    public List<PriceOffer> findByRoom(String roomId, int limit) {
        Query query = Query.query(Criteria.where("roomId").is(roomId))
                .with(NEWEST_FIRST)
                .limit(Math.max(1, limit));
        return mongo.find(query, OfferDocument.class).stream()
                .map(OfferPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<PriceOffer> findByListingForParty(String listingId, String accountId, int limit) {
        Query query = Query.query(new Criteria()
                        .andOperator(
                                Criteria.where("listingId").is(listingId),
                                new Criteria()
                                        .orOperator(
                                                Criteria.where("sellerId").is(accountId),
                                                Criteria.where("buyerId").is(accountId))))
                .with(NEWEST_FIRST)
                .limit(Math.max(1, limit));
        return mongo.find(query, OfferDocument.class).stream()
                .map(OfferPersistenceAdapter::toDomain)
                .toList();
    }

    private static Instant millis(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    private static OfferDocument toDocument(PriceOffer offer) {
        return new OfferDocument(
                offer.id(),
                offer.listingId(),
                offer.roomId(),
                offer.buyerId(),
                offer.sellerId(),
                offer.price(),
                offer.listingPriceAtOffer(),
                offer.origin().name(),
                offer.status().name(),
                millis(offer.createdAt()),
                millis(offer.respondedAt()),
                millis(offer.expiresAt()));
    }

    private static PriceOffer toDomain(OfferDocument document) {
        return new PriceOffer(
                document.getId(),
                document.getListingId(),
                document.getRoomId(),
                document.getBuyerId(),
                document.getSellerId(),
                document.getPrice(),
                document.getListingPriceAtOffer(),
                OfferOrigin.valueOf(document.getOrigin()),
                OfferStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                document.getRespondedAt(),
                document.getExpiresAt());
    }
}
