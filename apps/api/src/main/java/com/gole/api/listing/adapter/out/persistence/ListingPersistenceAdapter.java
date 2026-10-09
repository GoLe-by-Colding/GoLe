package com.gole.api.listing.adapter.out.persistence;

import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
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

    /**
     * 제목·설명 글자 검색. 검색어가 세트 번호처럼 생겼으면 카탈로그 세트 번호(변형 번호 접미사 포함)도 함께 본다 —
     * 제목에 번호를 쓰지 않은 매물도 번호로 찾히게 한다. "10307-1"·"#10307"은 기본 번호 "10307"로 글자도 찾는다.
     */
    private static Criteria textCriteria(ListingSearchQuery query) {
        String setNumber = query.setNumberInText();
        String text = setNumber == null ? query.text().trim() : setNumber;
        String escaped = Pattern.quote(text);
        List<Criteria> any = new ArrayList<>();
        any.add(Criteria.where("title").regex(escaped, "i"));
        any.add(Criteria.where("description").regex(escaped, "i"));
        if (setNumber != null) {
            any.add(Criteria.where("catalogSetNumber").regex("^" + Pattern.quote(setNumber) + "(-\\d+)?$"));
        }
        return new Criteria().orOperator(any.toArray(Criteria[]::new));
    }

    @Override
    public List<Listing> search(ListingSearchQuery query) {
        // 검색은 항상 활성(ACTIVE) 리스팅만 대상으로 한다. (ListingSearchQuery 규약)
        Criteria criteria = Criteria.where("status").is(ListingStatus.ACTIVE.name());

        if (query.text() != null && !query.text().isBlank()) {
            criteria = new Criteria().andOperator(criteria, textCriteria(query));
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
    public boolean updateIfActive(Listing listing) {
        // 판매 중일 때만 수정 필드를 바꾼다. save()처럼 문서를 통째로 덮어쓰면 그 사이 잡힌 주문
        // 예약(RESERVED)을 ACTIVE로 되돌린다. (E4)
        ConditionDisclosure d = listing.getDisclosure();
        Update update = new Update()
                .set("title", listing.getTitle())
                .set("description", listing.getDescription())
                .set("priceAmount", listing.getPrice().amount())
                .set("priceCurrency", DEFAULT_CURRENCY)
                .set("condition", listing.getCondition().name())
                .set("completeness", d.completeness().name())
                .set("hasBox", d.hasBox())
                .set("hasManual", d.hasManual())
                .set("hasMissingParts", d.hasMissingParts())
                .set("missingPartsNote", d.missingPartsNote())
                .set("defectsNote", d.defectsNote())
                .set("photoUrls", listing.getPhotoUrls());
        setOrUnset(
                update,
                "interestTag",
                listing.getInterestTag() == null
                        ? null
                        : listing.getInterestTag().key());
        setOrUnset(
                update,
                "previousPrice",
                listing.getPreviousPrice() == null
                        ? null
                        : listing.getPreviousPrice().amount());
        setOrUnset(update, "priceChangedAt", listing.getPriceChangedAt());
        // 변경 없는 수정(같은 값 재제출)도 성공이다 — modified가 아니라 matched를 본다.
        return mongoTemplate
                        .updateFirst(activeById(listing.getId()), update, ListingDocument.class)
                        .getMatchedCount()
                == 1;
    }

    @Override
    public boolean bumpIfActive(String listingId, Instant bumpedAt) {
        Update update = new Update().set("listedAt", bumpedAt).set("bumpedAt", bumpedAt);
        return mongoTemplate
                        .updateFirst(activeById(listingId), update, ListingDocument.class)
                        .getMatchedCount()
                == 1;
    }

    private static Query activeById(String listingId) {
        return new Query(Criteria.where("_id").is(listingId).and("status").is(ListingStatus.ACTIVE.name()));
    }

    private static void setOrUnset(Update update, String field, Object value) {
        if (value == null) {
            update.unset(field);
        } else {
            update.set(field, value);
        }
    }

    @Override
    public List<Listing> findActiveBySeller(String sellerId) {
        return repository.findBySellerIdAndStatusOrderByListedAtDesc(sellerId, ListingStatus.ACTIVE.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findBySeller(String sellerId) {
        // 삭제한 매물은 뺀다. 본인이 내린 것이 목록에 계속 남으면 시간이 갈수록 쓰레기만 쌓인다.
        return repository.findBySellerIdAndStatusNotOrderByListedAtDesc(sellerId, ListingStatus.DELETED.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findActiveBySellers(List<String> sellerIds, int limit) {
        if (sellerIds.isEmpty()) {
            return List.of();
        }
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return repository
                .findBySellerIdInAndStatusOrderByListedAtDesc(
                        sellerIds, ListingStatus.ACTIVE.name(), PageRequest.of(0, boundedLimit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Listing> findByIds(List<String> ids) {
        return repository.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    private Sort toSort(ListingSortOrder order) {
        return switch (order) {
            // 최신순 = 등록 또는 마지막 끌올 시각. createdAt으로 두면 끌올이 아무 효과가 없다. (B5)
            case NEWEST -> Sort.by(Sort.Direction.DESC, "listedAt");
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
                listing.getInterestTag() == null
                        ? null
                        : listing.getInterestTag().key(),
                listing.getStatus().name(),
                listing.getCreatedAt(),
                listing.getListedAt(),
                listing.getBumpedAt(),
                listing.getPreviousPrice() == null
                        ? null
                        : listing.getPreviousPrice().amount(),
                listing.getPriceChangedAt());
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
                toInterestTag(document.getInterestTag()),
                ListingStatus.valueOf(document.getStatus()),
                document.getCreatedAt(),
                // 백필 전 레거시 문서는 listedAt이 없다 — 도메인이 createdAt으로 본다. (B6)
                document.getListedAt(),
                document.getBumpedAt(),
                document.getPreviousPrice() == null ? null : Money.won(document.getPreviousPrice()),
                document.getPriceChangedAt());
    }

    /** 레거시/비정상 저장값 하나 때문에 매물 조회 전체가 실패하지 않도록 null로 흡수한다. */
    private InterestTag toInterestTag(String key) {
        try {
            return InterestTag.fromKey(key);
        } catch (com.gole.api.common.exception.BadRequestException ignored) {
            return null;
        }
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
