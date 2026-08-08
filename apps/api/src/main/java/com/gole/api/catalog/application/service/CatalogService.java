package com.gole.api.catalog.application.service;

import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.application.port.in.ListFeaturedLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase.LegoSetSummary;
import com.gole.api.catalog.application.port.in.SearchLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.UpdateLegoSetUseCase;
import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.application.port.out.LoadLegoSetPort;
import com.gole.api.catalog.domain.exception.LegoSetNotFoundException;
import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 카탈로그 유스케이스 구현. inbound port를 구현하고 outbound port에만 의존한다.
 * 횡단 관심사(로깅/시간측정)는 UseCaseLoggingAspect가 AOP로 처리한다.
 */
@Service
public class CatalogService
        implements FindLegoSetUseCase,
                SearchLegoSetsUseCase,
                ListFeaturedLegoSetsUseCase,
                CreateLegoSetUseCase,
                UpdateLegoSetUseCase,
                ListLegoSetsUseCase {

    private static final int FEATURED_LIMIT = 12;

    private final LoadLegoSetPort loadLegoSetPort;
    private final CatalogAdminPort catalogAdminPort;

    public CatalogService(LoadLegoSetPort loadLegoSetPort, CatalogAdminPort catalogAdminPort) {
        this.loadLegoSetPort = loadLegoSetPort;
        this.catalogAdminPort = catalogAdminPort;
    }

    @Override
    public String create(CreateLegoSetCommand command) {
        LegoSet set = new LegoSet(
                command.setNumber(),
                command.name(),
                command.theme(),
                command.pieceCount(),
                command.releaseYear(),
                command.retirementStatus(),
                command.imageUrl());
        return catalogAdminPort.save(set, command.featured()).getSetNumber();
    }

    @Override
    public List<LegoSetSummary> all() {
        return catalogAdminPort.findAll().stream()
                .map(stored -> new LegoSetSummary(stored.set(), stored.featured()))
                .toList();
    }

    @Override
    public LegoSet findBySetNumber(String setNumber) {
        return loadLegoSetPort.loadBySetNumber(setNumber).orElseThrow(() -> new LegoSetNotFoundException(setNumber));
    }

    @Override
    public List<LegoSet> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return loadLegoSetPort.searchByNameOrTheme(query.trim());
    }

    @Override
    public List<LegoSet> findFeatured() {
        return loadLegoSetPort.loadFeatured(FEATURED_LIMIT);
    }

    /** 세트 수정(관리자). 존재하지 않으면 404. (admin-console 요구사항 7.3) */
    @Override
    public void update(UpdateLegoSetCommand command) {
        requireExisting(command.setNumber());
        LegoSet updated = new LegoSet(
                command.setNumber(),
                command.name(),
                command.theme(),
                command.pieceCount(),
                command.releaseYear(),
                command.retirementStatus(),
                command.imageUrl());
        catalogAdminPort.save(updated, command.featured());
    }

    /** 홈 추천 토글(관리자). 다른 필드는 그대로 두고 플래그만 바꾼다. (요구사항 7.4) */
    @Override
    public void setFeatured(String setNumber, boolean featured) {
        catalogAdminPort.save(requireExisting(setNumber), featured);
    }

    private LegoSet requireExisting(String setNumber) {
        return loadLegoSetPort.loadBySetNumber(setNumber).orElseThrow(() -> new LegoSetNotFoundException(setNumber));
    }
}
