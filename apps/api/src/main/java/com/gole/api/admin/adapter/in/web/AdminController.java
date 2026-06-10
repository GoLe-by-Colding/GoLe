package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminDtos.AccountRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.CreateSetRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.LegoSetResponse;
import com.gole.api.admin.adapter.in.web.AdminDtos.ListingRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.OrderRow;
import com.gole.api.admin.adapter.in.web.AdminDtos.OverviewResponse;
import com.gole.api.admin.adapter.in.web.AdminDtos.PostRow;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase.CreateLegoSetCommand;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API. {@code /api/admin/**} 는 AdminAuthInterceptor 가 ADMIN 권한을 강제한다.
 * 운영 대시보드(집계/지표), 주문 모니터링, 매물 모더레이션(takedown), 카탈로그 관리를 제공한다.
 *
 * <p>읽기 전용 운영 뷰는 컬렉션을 직접 집계(read model)하고, 상태 변경(takedown)은
 * 해당 컨텍스트의 인바운드 유스케이스를 통해 도메인 규칙을 거친다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final List<String> COLLECTIONS =
            List.of("accounts", "lego_sets", "listings", "orders", "posts", "reviews", "price_transactions");
    private static final int MAX_ROWS = 100;

    private final MongoTemplate mongoTemplate;
    private final CreateLegoSetUseCase createLegoSetUseCase;
    private final ListLegoSetsUseCase listLegoSetsUseCase;
    private final DeleteListingUseCase deleteListingUseCase;

    public AdminController(
            MongoTemplate mongoTemplate,
            CreateLegoSetUseCase createLegoSetUseCase,
            ListLegoSetsUseCase listLegoSetsUseCase,
            DeleteListingUseCase deleteListingUseCase) {
        this.mongoTemplate = mongoTemplate;
        this.createLegoSetUseCase = createLegoSetUseCase;
        this.listLegoSetsUseCase = listLegoSetsUseCase;
        this.deleteListingUseCase = deleteListingUseCase;
    }

    /** 대시보드 집계: 컬렉션 카운트 + 비즈 지표(GMV·주문상태·활성매물). */
    @GetMapping("/overview")
    public OverviewResponse overview() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String c : COLLECTIONS) {
            counts.put(c, mongoTemplate.getCollection(c).estimatedDocumentCount());
        }

        // 주문 상태별 집계 + 완료 주문 거래액(GMV)
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        long gmv = 0L;
        AggregationResults<Document> agg = mongoTemplate.aggregate(
                Aggregation.newAggregation(Aggregation.group("status")
                        .count()
                        .as("count")
                        .sum("amount")
                        .as("sum")),
                "orders",
                Document.class);
        for (Document row : agg) {
            String status = row.getString("_id");
            long count = toLong(row.get("count"));
            ordersByStatus.put(status, count);
            if ("COMPLETED".equals(status)) {
                gmv += toLong(row.get("sum"));
            }
        }

        long activeListings = mongoTemplate.getCollection("listings").countDocuments(new Document("status", "ACTIVE"));

        return new OverviewResponse(counts, gmv, ordersByStatus, activeListings);
    }

    /** 최근 주문 모니터링(읽기 전용). */
    @GetMapping("/orders")
    public List<OrderRow> orders(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(clamp(limit));
        return mongoTemplate.find(q, Document.class, "orders").stream()
                .map(OrderRow::from)
                .toList();
    }

    /** 최근 매물 모니터링(모든 상태, 읽기 전용). */
    @GetMapping("/listings")
    public List<ListingRow> listings(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(clamp(limit));
        return mongoTemplate.find(q, Document.class, "listings").stream()
                .map(ListingRow::from)
                .toList();
    }

    /** 매물 강제 내림(모더레이션). 도메인 규칙(진행 중 주문 등)은 유스케이스가 강제한다. */
    @PostMapping("/listings/{listingId}/takedown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void takedown(@PathVariable String listingId) {
        deleteListingUseCase.delete(listingId);
    }

    /** 최근 게시글 모더레이션(읽기 전용). */
    @GetMapping("/posts")
    public List<PostRow> posts(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(clamp(limit));
        return mongoTemplate.find(q, Document.class, "posts").stream()
                .map(PostRow::from)
                .toList();
    }

    /** 게시글 강제 삭제(어드민 오버라이드 — 작성자 확인 없이 status=DELETED). */
    @PostMapping("/posts/{postId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePost(@PathVariable String postId) {
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id")
                                .is(postId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", "DELETED"),
                "posts");
    }

    /** 회원 목록(최근 가입순). */
    @GetMapping("/accounts")
    public List<AccountRow> accounts(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(clamp(limit));
        return mongoTemplate.find(q, Document.class, "accounts").stream()
                .map(AccountRow::from)
                .toList();
    }

    /** 회원 잠금 토글(로그인 불가 처리 — lockedUntil 을 far-future 로 설정). */
    @PostMapping("/accounts/{accountId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lockAccount(@PathVariable String accountId) {
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id")
                                .is(accountId)),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("lockedUntil", java.time.Instant.parse("9999-12-31T00:00:00Z")),
                "accounts");
    }

    /** 회원 잠금 해제. */
    @PostMapping("/accounts/{accountId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlockAccount(@PathVariable String accountId) {
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id")
                                .is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().unset("lockedUntil"),
                "accounts");
    }

    @GetMapping("/catalog/sets")
    public List<LegoSetResponse> listSets() {
        return listLegoSetsUseCase.all().stream().map(LegoSetResponse::from).toList();
    }

    @PostMapping("/catalog/sets")
    @ResponseStatus(HttpStatus.CREATED)
    public LegoSetResponse createSet(@Valid @RequestBody CreateSetRequest request) {
        String setNumber = createLegoSetUseCase.create(new CreateLegoSetCommand(
                request.setNumber(),
                request.name(),
                request.theme(),
                request.pieceCount(),
                request.releaseYear(),
                request.retirementStatus(),
                request.imageUrl(),
                request.featured()));
        return listLegoSetsUseCase.all().stream()
                .filter(s -> s.getSetNumber().equals(setNumber))
                .findFirst()
                .map(LegoSetResponse::from)
                .orElseThrow();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_ROWS));
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
