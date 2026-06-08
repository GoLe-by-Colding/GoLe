package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminDtos.CreateSetRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.LegoSetResponse;
import com.gole.api.admin.adapter.in.web.AdminDtos.OverviewResponse;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase.CreateLegoSetCommand;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 API. {@code /api/admin/**} 는 AdminAuthInterceptor 가 ADMIN 권한을 강제한다.
 * 대시보드 집계와 카탈로그 세트 관리를 제공한다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final List<String> COLLECTIONS = List.of(
            "accounts", "lego_sets", "listings", "orders", "posts", "reviews", "price_transactions");

    private final MongoTemplate mongoTemplate;
    private final CreateLegoSetUseCase createLegoSetUseCase;
    private final ListLegoSetsUseCase listLegoSetsUseCase;

    public AdminController(
            MongoTemplate mongoTemplate,
            CreateLegoSetUseCase createLegoSetUseCase,
            ListLegoSetsUseCase listLegoSetsUseCase) {
        this.mongoTemplate = mongoTemplate;
        this.createLegoSetUseCase = createLegoSetUseCase;
        this.listLegoSetsUseCase = listLegoSetsUseCase;
    }

    /** 대시보드 집계: 컬렉션별 도큐먼트 수. */
    @GetMapping("/overview")
    public OverviewResponse overview() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String c : COLLECTIONS) {
            counts.put(c, mongoTemplate.getCollection(c).estimatedDocumentCount());
        }
        return new OverviewResponse(counts);
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
}
