package com.gole.api.listing.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.account.adapter.in.web.RequiresOnboarding;
import com.gole.api.account.adapter.in.web.RequiresVerifiedSellerIdentity;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.listing.adapter.in.web.ListingRequests.CreateListingRequest;
import com.gole.api.listing.adapter.in.web.ListingRequests.UpdateListingRequest;
import com.gole.api.listing.application.port.in.BrowseListingsUseCase;
import com.gole.api.listing.application.port.in.BumpListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.ReviseListingUseCase;
import com.gole.api.listing.application.port.in.ReviseListingUseCase.ReviseListingCommand;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inbound 어댑터(REST). use case 인터페이스에만 의존한다. */
@Tag(name = "Listing", description = "매물 등록·검색·조회·수정·끌올·완료·삭제")
@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final CreateListingUseCase createListingUseCase;
    private final GetListingUseCase getListingUseCase;
    private final SearchListingsUseCase searchListingsUseCase;
    private final MarkListingSoldUseCase markListingSoldUseCase;
    private final DeleteListingUseCase deleteListingUseCase;
    private final BrowseListingsUseCase browseListingsUseCase;
    private final ReviseListingUseCase reviseListingUseCase;
    private final BumpListingUseCase bumpListingUseCase;

    public ListingController(
            CreateListingUseCase createListingUseCase,
            GetListingUseCase getListingUseCase,
            SearchListingsUseCase searchListingsUseCase,
            MarkListingSoldUseCase markListingSoldUseCase,
            DeleteListingUseCase deleteListingUseCase,
            BrowseListingsUseCase browseListingsUseCase,
            ReviseListingUseCase reviseListingUseCase,
            BumpListingUseCase bumpListingUseCase) {
        this.createListingUseCase = createListingUseCase;
        this.getListingUseCase = getListingUseCase;
        this.searchListingsUseCase = searchListingsUseCase;
        this.markListingSoldUseCase = markListingSoldUseCase;
        this.deleteListingUseCase = deleteListingUseCase;
        this.browseListingsUseCase = browseListingsUseCase;
        this.reviseListingUseCase = reviseListingUseCase;
        this.bumpListingUseCase = bumpListingUseCase;
    }

    @Operation(summary = "매물 등록", description = "판매할 브릭 상품을 등록합니다. sellerId는 로그인 계정 ID.")
    @PostMapping
    @RequiresOnboarding // onboarding D5, R9
    @RequiresVerifiedSellerIdentity
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@Valid @RequestBody CreateListingRequest request, HttpServletRequest http) {
        String id = createListingUseCase.create(new CreateListingCommand(
                AuthenticatedUser.id(http),
                request.title(),
                request.description(),
                request.price(),
                request.condition(),
                new ConditionDisclosure(
                        request.completeness() == null ? Completeness.NO_BOX : request.completeness(),
                        request.hasBox(),
                        request.hasManual(),
                        request.hasMissingParts(),
                        request.missingPartsNote(),
                        request.defectsNote()),
                request.photoKeys(),
                request.catalogSetNumber(),
                com.gole.api.listing.domain.model.ListingCategory.fromKey(request.category()),
                InterestTag.fromKey(request.interestTag())));
        return toResponse(getListingUseCase.getById(id));
    }

    @Operation(
            summary = "매물 검색",
            description = "활성 매물을 검색합니다. 파라미터 없으면 최신순 전체 반환.\n\n"
                    + "- `query`: 제목·설명 텍스트 검색. 세트 번호 형태(`75192`·`#75192`·`10307-1`)면 카탈로그 세트 번호도 찾는다\n"
                    + "- `condition`: new_sealed | like_new | used_good | used_fair | damaged\n"
                    + "  (레거시 used_complete → used_good, used_incomplete → used_fair 로 매핑)\n"
                    + "- `category`: set | parts | minifig | moc\n"
                    + "- `setNumber`: 카탈로그 세트번호(예: 10307) — 세트 상세 페이지용\n"
                    + "- `sort`: NEWEST | PRICE_ASC | PRICE_DESC")
    @GetMapping
    public List<ListingResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "condition", required = false) ItemCondition condition,
            @RequestParam(value = "minPrice", required = false) Long minPrice,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "sort", required = false) ListingSortOrder sort,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "setNumber", required = false) String setNumber) {
        ListingSearchQuery searchQuery = new ListingSearchQuery(
                query,
                condition,
                minPrice,
                maxPrice,
                sort,
                category == null ? null : com.gole.api.listing.domain.model.ListingCategory.fromKey(category),
                setNumber);
        return searchListingsUseCase.search(searchQuery).stream()
                .map(this::toResponse)
                .toList();
    }

    @Operation(
            summary = "내 매물",
            description = "로그인 계정이 등록한 매물 전체(상태 무관, 최신순). 프로필의 '내 매물' 탭에 쓴다.\n\n"
                    + "검색(`GET /api/v1/listings`)은 활성 매물만 돌려주므로 판매완료·예약중 매물이 빠진다.\n"
                    + "대상 셀러는 쿼리 파라미터가 아니라 인증 세션에서 정한다 — 남의 비활성 매물을 볼 수 없다.")
    @GetMapping("/mine")
    public List<ListingResponse> listMine(HttpServletRequest http) {
        return browseListingsUseCase.bySeller(AuthenticatedUser.id(http)).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{listingId}")
    public ListingResponse get(@PathVariable String listingId) {
        return toResponse(getListingUseCase.getPublicById(listingId));
    }

    /**
     * 매물 수정. 본문 필드를 통째로 교체한다. (listing-edit-and-bump E1~E9)
     *
     * <p>{@code @RequiresVerifiedSellerIdentity}는 붙이지 않는다. 수정은 새 판매가 아니라 이미 본인
     * 확인을 거쳐 등록된 매물의 내용 정정이다 — 판매 완료·삭제와 같은 취급이다. 등록과 같이
     * 온보딩은 요구한다(거래성 액션).
     */
    @Operation(
            summary = "매물 수정",
            description =
                    "본인의 판매 중(ACTIVE) 매물을 수정합니다. 필드를 통째로 교체하며 검증은 등록과 같습니다.\n\n"
                            + "- `catalogSetNumber`·`category`는 수정할 수 없고 본문에 와도 무시합니다.\n"
                            + "- `photoKeys`는 수정 후 전체 사진 목록(저장 키)입니다. 응답의 `photoKeys`를 그대로 쓰면 됩니다.\n"
                            + "- 가격이 내려가면 찜한 사용자에게 알림이 갑니다. 관심 테마를 바꿔도 알림톡은 다시 가지 않습니다.\n"
                            + "- 403 `LISTING_ACCESS_DENIED`, 409 `LISTING_ORDER_IN_PROGRESS`(주문 진행 중)·`LISTING_NOT_EDITABLE`(판매 완료·삭제)")
    @PutMapping("/{listingId}")
    @RequiresOnboarding // onboarding D5, R9 — 등록과 같은 거래성 액션
    public ListingResponse update(
            @PathVariable String listingId, @Valid @RequestBody UpdateListingRequest request, HttpServletRequest http) {
        ReviseListingUseCase.RevisionResult result = reviseListingUseCase.revise(new ReviseListingCommand(
                listingId,
                AuthenticatedUser.id(http),
                request.title(),
                request.description(),
                request.price(),
                request.condition(),
                new ConditionDisclosure(
                        request.completeness() == null ? Completeness.NO_BOX : request.completeness(),
                        request.hasBox(),
                        request.hasManual(),
                        request.hasMissingParts(),
                        request.missingPartsNote(),
                        request.defectsNote()),
                request.photoKeys(),
                InterestTag.fromKey(request.interestTag())));
        return toResponse(result.listing());
    }

    @Operation(
            summary = "매물 끌올",
            description = "본인의 판매 중 매물을 최신순 맨 위로 올립니다(`listedAt`을 지금으로).\n\n"
                    + "- 쿨다운은 `listedAt`(등록 또는 마지막 끌올) 기준이며, 다음 가능 시각은 응답의 `bumpAvailableAt`입니다.\n"
                    + "- 429 `LISTING_BUMP_COOLDOWN` + `Retry-After`(초), 403 `LISTING_ACCESS_DENIED`,\n"
                    + "  409 `LISTING_ORDER_IN_PROGRESS`·`LISTING_NOT_BUMPABLE`")
    @PostMapping("/{listingId}/bump")
    public ListingResponse bump(@PathVariable String listingId, HttpServletRequest http) {
        return toResponse(bumpListingUseCase.bump(listingId, AuthenticatedUser.id(http)));
    }

    @PostMapping("/{listingId}/sold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSold(@PathVariable String listingId, HttpServletRequest http) {
        requireSeller(listingId, http);
        markListingSoldUseCase.markSold(listingId);
    }

    @DeleteMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String listingId, HttpServletRequest http) {
        requireSeller(listingId, http);
        deleteListingUseCase.delete(listingId);
    }

    private ListingResponse toResponse(Listing listing) {
        return ListingResponse.from(listing, bumpListingUseCase.bumpCooldown());
    }

    private void requireSeller(String listingId, HttpServletRequest http) {
        if (!getListingUseCase.getById(listingId).getSellerId().equals(AuthenticatedUser.id(http))) {
            throw new ForbiddenException("LISTING_ACCESS_DENIED", "본인의 매물만 처리할 수 있습니다");
        }
    }
}
