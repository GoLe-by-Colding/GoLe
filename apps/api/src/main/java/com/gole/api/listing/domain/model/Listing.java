package com.gole.api.listing.domain.model;

import com.gole.api.listing.domain.exception.ListingBumpCooldownException;
import com.gole.api.listing.domain.exception.ListingConflictException;
import com.gole.api.listing.domain.exception.ListingStateException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 리스팅 애그리거트 루트. 생성 불변식과 상태 전이를 캡슐화한다. (요구사항 5)
 * 프레임워크에 의존하지 않는 순수 도메인.
 */
public final class Listing {

    private final String id;
    private final String sellerId;
    // 판매자 수정(revise)으로 통째로 바뀌는 필드. 세트 번호·카테고리는 바꾸지 않는다(E2).
    private String title;
    private String description;
    private Money price;
    private ItemCondition condition;
    private ConditionDisclosure disclosure;
    private List<String> photoUrls;
    private final String catalogSetNumber; // nullable
    private final ListingCategory category;
    private InterestTag interestTag; // nullable
    private final Instant createdAt;
    private ListingStatus status;
    /** "최신순" 정렬 키. 등록 시각이었다가 끌올하면 그 시각이 된다. (B1, B5) */
    private Instant listedAt;

    private Instant bumpedAt; // nullable — 한 번도 끌올하지 않았으면 비어 있다
    private Money previousPrice; // nullable — 지금 가격이 직전보다 쌀 때만 있다(E6)
    private Instant priceChangedAt; // nullable

    public Listing(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category,
            ListingStatus status,
            Instant createdAt) {
        this(
                id,
                sellerId,
                title,
                description,
                price,
                condition,
                disclosure,
                photoUrls,
                catalogSetNumber,
                category,
                null,
                status,
                createdAt);
    }

    public Listing(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category,
            InterestTag interestTag,
            ListingStatus status,
            Instant createdAt) {
        this(
                id,
                sellerId,
                title,
                description,
                price,
                condition,
                disclosure,
                photoUrls,
                catalogSetNumber,
                category,
                interestTag,
                status,
                createdAt,
                null,
                null,
                null,
                null);
    }

    /**
     * 저장소 복원용 전체 생성자.
     *
     * @param listedAt       정렬 키. null이면(끌올 도입 전 문서) {@code createdAt}으로 본다(B6).
     * @param bumpedAt       마지막 끌올 시각(nullable)
     * @param previousPrice  직전 가격(nullable)
     * @param priceChangedAt 마지막 가격 변경 시각(nullable)
     */
    public Listing(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category,
            InterestTag interestTag,
            ListingStatus status,
            Instant createdAt,
            Instant listedAt,
            Instant bumpedAt,
            Money previousPrice,
            Instant priceChangedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.sellerId = requireText(sellerId, "sellerId");
        this.title = requireText(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.price = Objects.requireNonNull(price, "price");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.disclosure = disclosure == null ? ConditionDisclosure.basic() : disclosure;
        if ((photoUrls == null || photoUrls.isEmpty()) && status != ListingStatus.DELETED) {
            throw new MissingPhotoException(); // 요구사항 5.2
        }
        this.photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
        this.catalogSetNumber = catalogSetNumber;
        this.category = category == null ? ListingCategory.SET : category;
        this.interestTag = interestTag;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.listedAt = listedAt == null ? createdAt : listedAt;
        this.bumpedAt = bumpedAt;
        this.previousPrice = previousPrice;
        this.priceChangedAt = priceChangedAt;
    }

    /** 신규 리스팅: ACTIVE 상태로 생성. (요구사항 5.1) */
    public static Listing create(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category,
            Instant createdAt) {
        return create(
                id,
                sellerId,
                title,
                description,
                price,
                condition,
                disclosure,
                photoUrls,
                catalogSetNumber,
                category,
                null,
                createdAt);
    }

    public static Listing create(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            ConditionDisclosure disclosure,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingCategory category,
            InterestTag interestTag,
            Instant createdAt) {
        return new Listing(
                id,
                sellerId,
                title,
                description,
                price,
                condition,
                disclosure,
                photoUrls,
                catalogSetNumber,
                category,
                interestTag,
                ListingStatus.ACTIVE,
                createdAt);
    }

    /** 판매 완료 처리. (요구사항 5.6) */
    public void markSold() {
        if (status == ListingStatus.DELETED) {
            throw new ListingStateException("LISTING_DELETED", "Deleted listing cannot be sold");
        }
        this.status = ListingStatus.SOLD;
    }

    /** 삭제. 진행 중 주문(RESERVED)이 있으면 거부. (요구사항 5.7, 5.8) */
    public void delete() {
        if (status == ListingStatus.RESERVED) {
            throw new ListingStateException(
                    "LISTING_ORDER_IN_PROGRESS", "Listing with an in-progress order cannot be deleted");
        }
        this.status = ListingStatus.DELETED;
        this.photoUrls = List.of();
    }

    /**
     * 운영자 강제 내림. (admin-console 요구사항 4.3, 4.4)
     *
     * <p>{@link #delete()}와 달리 진행 중 주문(RESERVED)이어도 내린다. 가품·도용 신고 대응은
     * 거래 진행 여부보다 우선하기 때문이다. 진행 중이던 주문의 환불/정산은 주문 컨텍스트가 별도로 처리한다.
     * 이미 내려간 매물이면 아무 일도 하지 않는다(멱등).
     */
    public void takedown() {
        this.status = ListingStatus.DELETED;
        this.photoUrls = List.of();
    }

    /** 선점 해제(RESERVED → ACTIVE). 결제 실패/환불 시. 이미 활성이면 무시(멱등). */
    public void release() {
        if (status == ListingStatus.RESERVED) {
            this.status = ListingStatus.ACTIVE;
        }
    }

    /**
     * 판매자 수정. 수정 가능한 필드를 통째로 교체하고 가격 이력을 남긴다. (E1, E3, E6)
     *
     * <p>가격이 내려가면 직전 가격과 변경 시각을 기록하고, 올라가면 직전 가격을 비운다 — 인하
     * 표시는 "지금 가격이 직전보다 싸다"일 때만 보여야 하기 때문이다. 같으면 둘 다 그대로 둔다.
     * 수정은 새 매물이 아니므로 {@code listedAt}(정렬 키)은 건드리지 않는다.
     *
     * <p>입력을 전부 검증한 뒤에 필드를 바꾼다. 중간에 실패해도 반쯤 바뀐 매물이 남지 않는다.
     *
     * @return 이번 수정의 가격 변화. 인하 후속 처리(찜한 사람 알림)의 근거.
     */
    public PriceChange revise(ListingRevision revision, Instant now) {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(now, "now");
        requireEditable();
        String nextTitle = requireText(revision.title(), "title");
        if (revision.photoKeys().isEmpty()) {
            throw new MissingPhotoException(); // 요구사항 5.2 — 수정으로도 사진을 0장으로 만들 수 없다
        }

        PriceChange change = new PriceChange(price, revision.price());
        this.title = nextTitle;
        this.description = revision.description();
        this.price = revision.price();
        this.condition = revision.condition();
        this.disclosure = revision.disclosure();
        this.photoUrls = revision.photoKeys();
        this.interestTag = revision.interestTag();
        if (change.dropped()) {
            this.previousPrice = change.before();
            this.priceChangedAt = now;
        } else if (change.raised()) {
            this.previousPrice = null;
            this.priceChangedAt = now;
        }
        return change;
    }

    /**
     * 끌올. 쿨다운이 지났으면 정렬 키를 지금으로 올린다. (B1~B3)
     *
     * <p>쿨다운 기준은 {@code listedAt}(등록 또는 마지막 끌올)이다. 그래서 등록 직후 끌올도 같은
     * 규칙으로 막힌다. 경계 시각(정확히 {@code listedAt + cooldown})부터 허용한다.
     */
    public void bump(Instant now, Duration cooldown) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cooldown, "cooldown");
        requireBumpable();
        Instant availableAt = bumpAvailableAt(cooldown);
        if (now.isBefore(availableAt)) {
            throw new ListingBumpCooldownException(Duration.between(now, availableAt));
        }
        this.bumpedAt = now;
        this.listedAt = now;
    }

    /** 다음 끌올이 가능한 시각(= {@code listedAt + cooldown}). 응답의 {@code bumpAvailableAt}. */
    public Instant bumpAvailableAt(Duration cooldown) {
        return listedAt.plus(cooldown);
    }

    /** 수정 가능한 상태인지 확인한다. RESERVED → 주문 진행 중, SOLD·DELETED → 수정 불가. (E3) */
    public void requireEditable() {
        if (status == ListingStatus.RESERVED) {
            throw ListingConflictException.orderInProgress();
        }
        if (status != ListingStatus.ACTIVE) {
            throw ListingConflictException.notEditable();
        }
    }

    /** 끌올 가능한 상태인지 확인한다. RESERVED → 주문 진행 중, 그 밖의 비활성 → 끌올 불가. (B3) */
    public void requireBumpable() {
        if (status == ListingStatus.RESERVED) {
            throw ListingConflictException.orderInProgress();
        }
        if (status != ListingStatus.ACTIVE) {
            throw ListingConflictException.notBumpable();
        }
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE;
    }

    /**
     * 공개 상세·문의·새 채팅에서 노출할 수 있는 상태인지 확인한다.
     *
     * <p>예약·판매 완료 매물은 기존 거래 기록과 대화를 위해 계속 보이지만, 셀러가 삭제하거나
     * 운영자가 내린 매물은 공개 표면에서 숨긴다.
     */
    public boolean isPubliclyVisible() {
        return status != ListingStatus.DELETED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public ConditionDisclosure getDisclosure() {
        return disclosure;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public String getCatalogSetNumber() {
        return catalogSetNumber;
    }

    /**
     * 이 매물이 세트 한 벌을 거래할 때의 카탈로그 세트 번호. 미니피규어·부품·MOC 매물이면 {@code null}.
     *
     * <p>미니피규어·부품도 출처 세트 번호를 달 수 있다(검색·식별용). 하지만 그 거래를 세트 한 벌과 같은 것으로 보면
     * 완료 주문이 세트 체결가로 기록돼 시세가 무너지고, 세트 입찰을 미니피규어로 체결하거나 세트 입찰자에게
     * 엉뚱한 매칭 알림이 간다. 세트 한 벌로서의 거래가 필요한 곳(체결가·입찰 체결·입찰 매칭)은 이 값을 쓴다.
     */
    public String wholeSetNumber() {
        return category == ListingCategory.SET ? catalogSetNumber : null;
    }

    public ListingCategory getCategory() {
        return category;
    }

    public InterestTag getInterestTag() {
        return interestTag;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 정렬 키. 끌올 도입 전 문서는 저장값이 없으므로 {@code createdAt}으로 본다. (B6) */
    public Instant getListedAt() {
        return listedAt;
    }

    public Instant getBumpedAt() {
        return bumpedAt;
    }

    public Money getPreviousPrice() {
        return previousPrice;
    }

    public Instant getPriceChangedAt() {
        return priceChangedAt;
    }
}
