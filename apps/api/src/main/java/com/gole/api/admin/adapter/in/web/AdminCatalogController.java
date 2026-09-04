package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminDtos.CreateSetRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.FeaturedRequest;
import com.gole.api.admin.adapter.in.web.AdminDtos.LegoSetResponse;
import com.gole.api.admin.adapter.in.web.AdminDtos.UpdateSetRequest;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase.CreateLegoSetCommand;
import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.UpdateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.UpdateLegoSetUseCase.UpdateLegoSetCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * 카탈로그 관리 — 세트 목록/등록/수정/추천 토글. (admin-console 요구사항 7.2~7.5)
 */
@Tag(name = "Admin · 카탈로그", description = "브릭 세트 기준 정보 관리")
@RestController
@RequestMapping("/api/admin/catalog/sets")
public class AdminCatalogController {

    private final CreateLegoSetUseCase createLegoSet;
    private final UpdateLegoSetUseCase updateLegoSet;
    private final ListLegoSetsUseCase listLegoSets;
    private final FindLegoSetUseCase findLegoSet;
    private final RecordAdminActionUseCase audit;

    public AdminCatalogController(
            CreateLegoSetUseCase createLegoSet,
            UpdateLegoSetUseCase updateLegoSet,
            ListLegoSetsUseCase listLegoSets,
            FindLegoSetUseCase findLegoSet,
            RecordAdminActionUseCase audit) {
        this.createLegoSet = createLegoSet;
        this.updateLegoSet = updateLegoSet;
        this.listLegoSets = listLegoSets;
        this.findLegoSet = findLegoSet;
        this.audit = audit;
    }

    @GetMapping
    public List<LegoSetResponse> listSets(@RequestParam(defaultValue = "200") int limit) {
        return listLegoSets.all(limit).stream().map(LegoSetResponse::from).toList();
    }

    @Operation(summary = "세트 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegoSetResponse createSet(@Valid @RequestBody CreateSetRequest request, HttpServletRequest http) {
        String setNumber = createLegoSet.create(new CreateLegoSetCommand(
                request.setNumber(),
                request.name(),
                request.theme(),
                request.pieceCount(),
                request.releaseYear(),
                request.retirementStatus(),
                request.imageUrl(),
                request.featured()));
        record(http, AdminActionType.CATALOG_SET_CREATE, setNumber, null);
        return LegoSetResponse.from(findLegoSet.findBySetNumber(setNumber), request.featured());
    }

    @Operation(summary = "세트 수정", description = "존재하지 않는 세트면 404 LEGO_SET_NOT_FOUND.")
    @PostMapping("/{setNumber}")
    public LegoSetResponse updateSet(
            @PathVariable String setNumber, @Valid @RequestBody UpdateSetRequest request, HttpServletRequest http) {
        updateLegoSet.update(new UpdateLegoSetCommand(
                setNumber,
                request.name(),
                request.theme(),
                request.pieceCount(),
                request.releaseYear(),
                request.retirementStatus(),
                request.imageUrl(),
                request.featured()));
        record(http, AdminActionType.CATALOG_SET_UPDATE, setNumber, null);
        return LegoSetResponse.from(findLegoSet.findBySetNumber(setNumber), request.featured());
    }

    @Operation(summary = "홈 추천 토글", description = "featured 플래그만 갱신합니다.")
    @PostMapping("/{setNumber}/featured")
    public LegoSetResponse setFeatured(
            @PathVariable String setNumber, @RequestBody FeaturedRequest request, HttpServletRequest http) {
        updateLegoSet.setFeatured(setNumber, request.featured());
        record(http, AdminActionType.CATALOG_SET_FEATURE, setNumber, String.valueOf(request.featured()));
        return LegoSetResponse.from(findLegoSet.findBySetNumber(setNumber), request.featured());
    }

    private void record(HttpServletRequest http, AdminActionType type, String setNumber, String reason) {
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.CATALOG_SET, setNumber, reason));
    }
}
