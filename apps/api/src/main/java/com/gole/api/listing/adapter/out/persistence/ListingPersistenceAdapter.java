package com.gole.api.listing.adapter.out.persistence;

import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 리스팅 영속성 어댑터. 도메인 {@link Listing}과 {@link ListingDocument}를 양방향 매핑한다.
 *
 * <p>단순 조회는 {@link ListingMongoRepository} 파생 쿼리로, 복합 검색/원자적 선점은
 * {@link MongoTemplate}으로 처리한다.
 */
@Component
public class ListingPersistenceAdapter implements ListingRepositoryPort {

    private static final String DEFAULT_CURRENCY = "KRW";

    private final ListingMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public ListingPersistenceAdapter(ListingMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Listing save(Listing listing) {
        ListingDocument saved = repository.save(toDocument(listing));
        return toDomain(saved);
    }

    @Override
    public Optional<Listing> findById(String listingId) {
        return repository.findById(listingId).map(this::toDomain);
    }

    @Override
    public List<Listing> search(ListingSearchQuery query) {
        // 검색은 항상 활성(ACTIVE) 리스팅만 대상으로 한다. (ListingSearchQuery 규약)
        Criteria criteria = Criteria.where("status").is(ListingStatus.ACTIVE.name());

        if (query.text() != null && !query.text().isBlank()) {
            String escaped = Pattern.quote(query.text().trim());
            Criteria textCriteria = new Criteria()
                    .orOperator(
                            Criteria.where("title").regex(escaped, "i"),
                            Criteria.where("description").regex(escaped, "i"));
            criteria = new Criteria().andOperator(criteria, textCriteria);
        }

        if (query.condition() != null) {
            // 3단계 시절 저장값(USED_COMPLETE 등)도 함께 매칭해야 과거 매물이 필터에서 빠지지 않는다.
            criteria = criteria.and("condition").in(query.condition().storageNames());
        }

        if (query.category() != null) {
            criteria = criteria.and("category").is(query.category().name());
        }

        if (query.setNumber() != null) {
            criteria = criteria.and("catalogSetNumber").is(query.setNumber());
        }

        if (query.minPrice() != null || query.maxPrice() != null) {
            Criteria priceCriteria = Criteria.where("priceAmount");
            if (query.minPrice() != null) {
                priceCriteria = priceCriteria.gte(query.minPrice());
            }
            if (query.maxPrice() != null) {
                priceCriteria = priceCriteria.lte(query.maxPrice());
            }
            criteria = new Criteria().andOperator(criteria, priceCriteria);
        }

        Query mongoQuery = new Query(criteria).with(toSort(query.sort()));
        return mongoTemplate.find(mongoQuery, ListingDocument.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Listing> reserveIfActive(String listingId) {
        // ACTIVE → RESERVED 원자적 전이. 활성이 아니면 매칭 없음 → 비어있음.
        Query query =
                new Query(Criteria.where("_id").is(listingId).and("status").is(ListingStatus.ACTIVE.name()));
        Update update = Update.update("status", ListingStatus.RESERVED.name());
        ListingDocument updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ListingDocument.class);
        return Optional.ofNullable(updated).map(this::toDomain);
    }

    @Override
    public boolean markSoldIfActive(String listingId) {
        Query query =
                new Query(Criteria.where("_id").is(listingId).and("status").is(ListingStatus.ACTIVE.name()));
        return mongoTemplate
                        .updateFirst(query, Update.update("status", ListingStatus.SOLD.name()), ListingDocument.class)
                        .getModifiedCount()
                == 1;
    }

    @Override
    public List<Listing> findActiveBySeller(String sellerId) {
        return repository.findBySellerIdAndStatus(sellerId, ListingStatus.ACTIVE.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findBySeller(String sellerId) {
        // 삭제한 매물은 뺀다. 본인이 내린 것이 목록에 계속 남으면 시간이 갈수록 쓰레기만 쌓인다.
        return repository
                .findBySellerIdAndStatusNotOrderByCreatedAtDesc(sellerId, ListingStatus.DELETED.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findActiveBySellers(List<String> sellerIds) {
        return repository.findBySellerIdInAndStatus(sellerIds, ListingStatus.ACTIVE.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findByIds(List<String> ids) {
        return repository.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    private Sort toSort(ListingSortOrder order) {
        return switch (order) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "priceAmount");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "priceAmount");
        };
    }

    private ListingDocument toDocument(Listing listing) {
        ConditionDisclosure d = listing.getDisclosure();
        return new ListingDocument(
                listing.getId(),
                listing.getSellerId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getPrice().amount(),
                DEFAULT_CURRENCY,
                listing.getCondition().name(),
                d.completeness().name(),
                d.hasBox(),
                d.hasManual(),
                d.hasMissingParts(),
                d.missingPartsNote(),
                d.defectsNote(),
                listing.getPhotoUrls(),
                listing.getCatalogSetNumber(),
                listing.getCategory().name(),
                listing.getStatus().name(),
                listing.getCreatedAt());
    }

    private Listing toDomain(ListingDocument document) {
        return new Listing(
                document.getId(),
                document.getSellerId(),
                document.getTitle(),
                document.getDescription(),
                Money.won(document.getPriceAmount()),
                // valueOf가 아니라 fromKey — 레거시 값(USED_COMPLETE 등)에서 예외가 나지 않게.
                ItemCondition.fromKey(document.getCondition()),
                toDisclosure(document),
                document.getPhotoUrls(),
                document.getCatalogSetNumber(),
                ListingCategory.fromKey(document.getCategory()),
                ListingStatus.valueOf(document.getStatus()),
                document.getCreatedAt());
    }

    /** 레거시 문서(고지 필드 없음)는 기본값으로 보정한다. */
    private ConditionDisclosure toDisclosure(ListingDocument d) {
        if (d.getCompleteness() == null) {
            return ConditionDisclosure.basic();
        }
        return new ConditionDisclosure(
                Completeness.valueOf(d.getCompleteness()),
                Boolean.TRUE.equals(d.getHasBox()),
                Boolean.TRUE.equals(d.getHasManual()),
                Boolean.TRUE.equals(d.getHasMissingParts()),
                d.getMissingPartsNote() == null ? "" : d.getMissingPartsNote(),
                d.getDefectsNote() == null ? "" : d.getDefectsNote());
    }
}
