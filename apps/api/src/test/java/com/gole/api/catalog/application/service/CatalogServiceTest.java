package com.gole.api.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.application.port.out.CatalogAdminPort.StoredLegoSet;
import com.gole.api.catalog.application.port.out.LoadLegoSetPort;
import com.gole.api.catalog.domain.exception.LegoSetNotFoundException;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 헥사고날의 이점: outbound port를 가짜 구현으로 대체해 도메인/유스케이스를
 * 프레임워크/DB 없이 순수하게 테스트한다.
 */
class CatalogServiceTest {

    private final LegoSet eiffel =
            new LegoSet("10307", "Eiffel Tower", "Icons", 10001, 2022, RetirementStatus.ACTIVE, null);

    @Test
    void findBySetNumber_returnsSet_whenPresent() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.of(eiffel), List.of()), new FakeAdminPort());

        LegoSet result = service.findBySetNumber("10307");

        assertThat(result.getName()).isEqualTo("Eiffel Tower");
        assertThat(result.isRetired()).isFalse();
    }

    @Test
    void findBySetNumber_throws_whenMissing() {
        CatalogService service = new CatalogService(new FakeLoadPort(Optional.empty(), List.of()), new FakeAdminPort());

        assertThatThrownBy(() -> service.findBySetNumber("99999")).isInstanceOf(LegoSetNotFoundException.class);
    }

    @Test
    void search_returnsEmpty_forBlankQuery() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.empty(), List.of(eiffel)), new FakeAdminPort());

        assertThat(service.search("  ")).isEmpty();
    }

    @Test
    void all_preservesFeaturedFlag_forAdminEditing() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.empty(), List.of()), new FakeAdminPort(eiffel));

        assertThat(service.all(200)).singleElement().satisfies(summary -> {
            assertThat(summary.set()).isEqualTo(eiffel);
            assertThat(summary.featured()).isTrue();
        });
    }

    private record FakeLoadPort(Optional<LegoSet> byNumber, List<LegoSet> bySearch) implements LoadLegoSetPort {

        @Override
        public Optional<LegoSet> loadBySetNumber(String setNumber) {
            return byNumber;
        }

        @Override
        public List<LegoSet> searchByNameOrTheme(String query) {
            return bySearch;
        }

        @Override
        public List<LegoSet> loadFeatured(int limit) {
            return bySearch;
        }
    }

    private static final class FakeAdminPort implements CatalogAdminPort {
        private final LegoSet stored;

        private FakeAdminPort() {
            this(null);
        }

        private FakeAdminPort(LegoSet stored) {
            this.stored = stored;
        }

        @Override
        public LegoSet save(LegoSet set, boolean featured) {
            return set;
        }

        @Override
        public List<StoredLegoSet> findAll(int limit) {
            return stored == null ? List.of() : List.of(new StoredLegoSet(stored, true));
        }
    }
}
