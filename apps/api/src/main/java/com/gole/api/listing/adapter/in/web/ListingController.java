package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.adapter.in.web.ListingRequests.CreateListingRequest;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.application.port.in.DeleteListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.application.query.ListingSortOrder;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST). use case 인터페이스에만 의존한다.
 */
@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final CreateListingUseCase createListingUseCase;
    private final GetListingUseCase getListingUseCase;
    private final SearchListingsUseCase searchListingsUseCase;
    private final MarkListingSoldUseCase markListingSoldUseCase;
    private final DeleteListingUseCase deleteListingUseCase;

    public ListingController(
            CreateListingUseCase createListingUseCase,
            GetListingUseCase getListingUseCase,
            SearchListingsUseCase searchListingsUseCase,
            MarkListingSoldUseCase markListingSoldUseCase,
            DeleteListingUseCase deleteListingUseCase) {
        this.createListingUseCase = createListingUseCase;
        this.getListingUseCase = getListingUseCase;
        this.searchListingsUseCase = searchListingsUseCase;
        this.markListingSoldUseCase = markListingSoldUseCase;
        this.deleteListingUseCase = deleteListingUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@Valid @RequestBody CreateListingRequest request) {
        String id = createListingUseCase.create(new CreateListingCommand(
                request.sellerId(),
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
                request.photoUrls(),
                request.catalogSetNumber(),
                com.gole.api.listing.domain.model.ListingCategory.fromKey(request.category())));
        return ListingResponse.from(getListingUseCase.getById(id));
    }

    /** 활성 리스팅 검색/필터/정렬. 파라미터가 없으면 최신순 전체. (요구사항 14) */
    @GetMapping
    public List<ListingResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "condition", required = false) ItemCondition condition,
            @RequestParam(value = "minPrice", required = false) Long minPrice,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "sort", required = false) ListingSortOrder sort,
            @RequestParam(value = "category", required = false) String category) {
        ListingSearchQuery searchQuery = new ListingSearchQuery(
                query,
                condition,
                minPrice,
                maxPrice,
                sort,
                category == null ? null : com.gole.api.listing.domain.model.ListingCategory.fromKey(category));
        return searchListingsUseCase.search(searchQuery).stream()
                .map(ListingResponse::from)
                .toList();
    }

    @GetMapping("/{listingId}")
    public ListingResponse get(@PathVariable String listingId) {
        return ListingResponse.from(getListingUseCase.getById(listingId));
    }

    @PostMapping("/{listingId}/sold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSold(@PathVariable String listingId) {
        markListingSoldUseCase.markSold(listingId);
    }

    @DeleteMapping("/{listingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String listingId) {
        deleteListingUseCase.delete(listingId);
    }
}
