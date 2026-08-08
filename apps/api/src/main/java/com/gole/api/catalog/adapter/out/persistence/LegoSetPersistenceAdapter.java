package com.gole.api.catalog.adapter.out.persistence;

import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.application.port.out.CatalogAdminPort.StoredLegoSet;
import com.gole.api.catalog.application.port.out.LoadLegoSetPort;
import com.gole.api.catalog.domain.model.LegoSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * Outbound 어댑터(MongoDB). 카탈로그 읽기/쓰기 포트를 구현하고 문서 ↔ 도메인 매핑을 담당한다.
 */
@Component
public class LegoSetPersistenceAdapter implements LoadLegoSetPort, CatalogAdminPort {

    private static final int SEARCH_LIMIT = 30;

    private final LegoSetMongoRepository repository;

    public LegoSetPersistenceAdapter(LegoSetMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public LegoSet save(LegoSet set, boolean featured) {
        LegoSetDocument saved = repository.save(LegoSetDocument.fromDomain(set, featured));
        return saved.toDomain();
    }

    @Override
    public List<StoredLegoSet> findAll() {
        return repository.findAll().stream()
                .map(document -> new StoredLegoSet(document.toDomain(), document.isFeatured()))
                .toList();
    }

    @Override
    public Optional<LegoSet> loadBySetNumber(String setNumber) {
        return repository.findById(setNumber).map(LegoSetDocument::toDomain);
    }

    @Override
    public List<LegoSet> searchByNameOrTheme(String query) {
        // 이름/테마 검색
        List<LegoSetDocument> byName = repository.findByNameContainingIgnoreCaseOrThemeContainingIgnoreCase(
                query, query, Limit.of(SEARCH_LIMIT));
        // 세트번호(id) 시작 검색 — 중복 제거
        Set<String> found =
                new HashSet<>(byName.stream().map(LegoSetDocument::getSetNumber).toList());
        List<LegoSet> result = new java.util.ArrayList<>(
                byName.stream().map(LegoSetDocument::toDomain).toList());
        repository.findBySetNumberStartingWith(Pattern.quote(query), Limit.of(SEARCH_LIMIT)).stream()
                .filter(d -> !found.contains(d.getSetNumber()))
                .map(LegoSetDocument::toDomain)
                .limit(Math.max(0, SEARCH_LIMIT - result.size()))
                .forEach(result::add);
        return result;
    }

    @Override
    public List<LegoSet> loadFeatured(int limit) {
        return repository.findByFeaturedIsTrue(Limit.of(limit)).stream()
                .map(LegoSetDocument::toDomain)
                .toList();
    }
}
