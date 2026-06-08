package com.gole.api.catalog.adapter.out.persistence;

import com.gole.api.catalog.application.port.out.LoadLegoSetPort;
import com.gole.api.catalog.domain.model.LegoSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Outbound 어댑터(MongoDB). LoadLegoSetPort를 구현하고 문서 ↔ 도메인 매핑을 담당한다.
 */
@Component
public class LegoSetPersistenceAdapter implements LoadLegoSetPort {

    private final LegoSetMongoRepository repository;

    public LegoSetPersistenceAdapter(LegoSetMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LegoSet> loadBySetNumber(String setNumber) {
        return repository.findById(setNumber).map(LegoSetDocument::toDomain);
    }

    @Override
    public List<LegoSet> searchByNameOrTheme(String query) {
        return repository
                .findByNameContainingIgnoreCaseOrThemeContainingIgnoreCase(query, query)
                .stream()
                .map(LegoSetDocument::toDomain)
                .toList();
    }

    @Override
    public List<LegoSet> loadFeatured(int limit) {
        return repository.findByFeaturedIsTrue(Limit.of(limit)).stream()
                .map(LegoSetDocument::toDomain)
                .toList();
    }
}
