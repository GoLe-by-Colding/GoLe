package com.gole.api.catalog.adapter.in.web;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.application.port.in.ListFeaturedLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.SearchLegoSetsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST). use case 인터페이스에만 의존한다.
 */
@Tag(name = "Catalog", description = "레고 카탈로그 세트 조회·검색")
@RestController
@RequestMapping("/api/v1/catalog/sets")
public class CatalogController {

    private final FindLegoSetUseCase findLegoSetUseCase;
    private final SearchLegoSetsUseCase searchLegoSetsUseCase;
    private final ListFeaturedLegoSetsUseCase listFeaturedLegoSetsUseCase;

    public CatalogController(
            FindLegoSetUseCase findLegoSetUseCase,
            SearchLegoSetsUseCase searchLegoSetsUseCase,
            ListFeaturedLegoSetsUseCase listFeaturedLegoSetsUseCase) {
        this.findLegoSetUseCase = findLegoSetUseCase;
        this.searchLegoSetsUseCase = searchLegoSetsUseCase;
        this.listFeaturedLegoSetsUseCase = listFeaturedLegoSetsUseCase;
    }

    @GetMapping("/featured")
    public List<LegoSetResponse> featured() {
        return listFeaturedLegoSetsUseCase.findFeatured().stream()
                .map(LegoSetResponse::from)
                .toList();
    }

    @GetMapping("/{setNumber}")
    public LegoSetResponse findBySetNumber(@PathVariable String setNumber) {
        return LegoSetResponse.from(findLegoSetUseCase.findBySetNumber(setNumber));
    }

    @GetMapping
    public List<LegoSetResponse> search(@RequestParam("query") String query) {
        return searchLegoSetsUseCase.search(query).stream()
                .map(LegoSetResponse::from)
                .toList();
    }
}
