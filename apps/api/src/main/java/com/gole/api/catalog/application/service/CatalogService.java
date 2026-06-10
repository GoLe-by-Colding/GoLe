package com.gole.api.catalog.application.service;

import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase;
import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.application.port.in.ListFeaturedLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase;
import com.gole.api.catalog.application.port.in.SearchLegoSetsUseCase;
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
    public List<LegoSet> all() {
        return catalogAdminPort.findAll();
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
}
