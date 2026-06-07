package com.gole.api.catalog.application.service;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.application.port.in.SearchLegoSetsUseCase;
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
public class CatalogService implements FindLegoSetUseCase, SearchLegoSetsUseCase {

    private final LoadLegoSetPort loadLegoSetPort;

    public CatalogService(LoadLegoSetPort loadLegoSetPort) {
        this.loadLegoSetPort = loadLegoSetPort;
    }

    @Override
    public LegoSet findBySetNumber(String setNumber) {
        return loadLegoSetPort.loadBySetNumber(setNumber)
                .orElseThrow(() -> new LegoSetNotFoundException(setNumber));
    }

    @Override
    public List<LegoSet> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return loadLegoSetPort.searchByNameOrTheme(query.trim());
    }
}
