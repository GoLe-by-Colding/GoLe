package com.gole.api.listing.adapter.in.web;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.listing.application.port.in.BumpListingUseCase;
import com.gole.api.listing.application.port.in.SearchListingPagesUseCase;
import com.gole.api.listing.application.query.ListingPage;
import com.gole.api.listing.application.query.ListingPageCursor;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.ListingCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Listing", description = "매물 등록·검색·조회·수정·끌올·완료·삭제")
@RestController
@RequestMapping("/api/v1/listings")
public class ListingPageController {

    private final SearchListingPagesUseCase searchListingPages;
    private final BumpListingUseCase bumpListingUseCase;

    public ListingPageController(SearchListingPagesUseCase searchListingPages, BumpListingUseCase bumpListingUseCase) {
        this.searchListingPages = searchListingPages;
        this.bumpListingUseCase = bumpListingUseCase;
    }

    @Operation(
            summary = "매물 검색 한 페이지",
            description = "`GET /api/v1/listings`와 같은 필터로 한 페이지를 돌려줍니다. 정렬 키가 같으면 ID로 순서를 고정합니다.\n\n"
                    + "- `limit`: 1~60, 기본 24. 범위를 벗어나면 가까운 끝값으로 당깁니다\n"
                    + "- `cursor`: 직전 응답의 `nextCursor`. 같은 필터·정렬로 보내야 합니다\n"
                    + "- 400 `LISTING_CURSOR_INVALID`: 해석할 수 없거나 다른 정렬의 커서")
    @GetMapping("/page")
    public ListingPageResponse searchPage(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "condition", required = false) ItemCondition condition,
            @RequestParam(value = "minPrice", required = false) Long minPrice,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "sort", required = false) ListingSortOrder sort,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "setNumber", required = false) String setNumber,
            @RequestParam(value = "limit", defaultValue = "" + ListingPage.DEFAULT_LIMIT) int limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        ListingSearchQuery searchQuery = new ListingSearchQuery(
                query,
                condition,
                minPrice,
                maxPrice,
                sort,
                category == null ? null : ListingCategory.fromKey(category),
                setNumber);
        Optional<ListingPageCursor> after = cursor == null || cursor.isBlank()
                ? Optional.empty()
                : Optional.of(ListingPageCursor.parse(cursor, searchQuery.sort())
                        .orElseThrow(() -> new BadRequestException("LISTING_CURSOR_INVALID", "매물 검색 커서가 올바르지 않습니다")));
        ListingPage page = searchListingPages.searchPage(searchQuery, after, limit);
        return new ListingPageResponse(
                page.items().stream()
                        .map(listing -> ListingResponse.from(listing, bumpListingUseCase.bumpCooldown()))
                        .toList(),
                page.next().map(ListingPageCursor::encode).orElse(null));
    }

    public record ListingPageResponse(List<ListingResponse> items, String nextCursor) {}
}
