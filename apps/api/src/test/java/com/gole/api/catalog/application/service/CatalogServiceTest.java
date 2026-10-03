package com.gole.api.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase.CreateLegoSetCommand;
import com.gole.api.catalog.application.port.in.UpdateLegoSetUseCase.UpdateLegoSetCommand;
import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.application.port.out.CatalogAdminPort.StoredLegoSet;
import com.gole.api.catalog.application.port.out.LoadLegoSetPort;
import com.gole.api.catalog.application.port.out.SetRetirementNotifierPort;
import com.gole.api.catalog.domain.exception.LegoSetNotFoundException;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 헥사고날의 이점: outbound port를 가짜 구현으로 대체해 도메인/유스케이스를
 * 프레임워크/DB 없이 순수하게 테스트한다.
 */
class CatalogServiceTest {

    private static final SetRetirementNotifierPort NO_NOTIFIER = (setNumber, setName, status) -> {};

    private final LegoSet eiffel =
            new LegoSet("10307", "Eiffel Tower", "Icons", 10001, 2022, RetirementStatus.ACTIVE, null);

    @Test
    void update_notifiesOnlyWhenRetirementStatusChangesAwayFromActive() {
        List<String> notices = new ArrayList<>();
        SetRetirementNotifierPort notifier = (setNumber, setName, status) -> notices.add(setNumber + ":" + status);
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.of(eiffel), List.of()), new FakeAdminPort(), notifier);

        service.update(updateTo(RetirementStatus.ACTIVE)); // 그대로 → 조용
        service.update(updateTo(RetirementStatus.RETIRING_SOON)); // ACTIVE → 임박
        service.update(updateTo(RetirementStatus.RETIRED)); // ACTIVE → 단종(가짜 로드 포트는 항상 ACTIVE)

        assertThat(notices).containsExactly("10307:RETIRING_SOON", "10307:RETIRED");
    }

    @Test
    void update_staysSilent_whenStatusUnchangedOrBackToActive() {
        List<String> notices = new ArrayList<>();
        LegoSet retiring =
                new LegoSet("10307", "Eiffel Tower", "Icons", 10001, 2022, RetirementStatus.RETIRING_SOON, null);
        CatalogService service = new CatalogService(
                new FakeLoadPort(Optional.of(retiring), List.of()),
                new FakeAdminPort(),
                (setNumber, setName, status) -> notices.add(setNumber + ":" + status));

        service.update(updateTo(RetirementStatus.RETIRING_SOON));
        service.update(updateTo(RetirementStatus.ACTIVE));

        assertThat(notices).isEmpty();
    }

    private static UpdateLegoSetCommand updateTo(RetirementStatus status) {
        return new UpdateLegoSetCommand("10307", "Eiffel Tower", "Icons", 10001, 2022, status, null, false);
    }

    @Test
    void findBySetNumber_returnsSet_whenPresent() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.of(eiffel), List.of()), new FakeAdminPort(), NO_NOTIFIER);

        LegoSet result = service.findBySetNumber("10307");

        assertThat(result.getName()).isEqualTo("Eiffel Tower");
        assertThat(result.isRetired()).isFalse();
    }

    @Test
    void findBySetNumber_throws_whenMissing() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.empty(), List.of()), new FakeAdminPort(), NO_NOTIFIER);

        assertThatThrownBy(() -> service.findBySetNumber("99999")).isInstanceOf(LegoSetNotFoundException.class);
    }

    @Test
    void search_returnsEmpty_forBlankQuery() {
        CatalogService service = new CatalogService(
                new FakeLoadPort(Optional.empty(), List.of(eiffel)), new FakeAdminPort(), NO_NOTIFIER);

        assertThat(service.search("  ")).isEmpty();
    }

    @Test
    void search_rejectsOversizedQueryBeforeRepositoryAccess() {
        CatalogService service = new CatalogService(
                new FakeLoadPort(Optional.empty(), List.of(eiffel)), new FakeAdminPort(), NO_NOTIFIER);

        assertThatThrownBy(() -> service.search("x".repeat(101)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100자");
    }

    @Test
    void all_preservesFeaturedFlag_forAdminEditing() {
        CatalogService service = new CatalogService(
                new FakeLoadPort(Optional.empty(), List.of()), new FakeAdminPort(eiffel), NO_NOTIFIER);

        assertThat(service.all(200)).singleElement().satisfies(summary -> {
            assertThat(summary.set()).isEqualTo(eiffel);
            assertThat(summary.featured()).isTrue();
        });
    }

    @Test
    void createAndUpdate_rejectExternalCatalogImageUrl() {
        CatalogService service =
                new CatalogService(new FakeLoadPort(Optional.of(eiffel), List.of()), new FakeAdminPort(), NO_NOTIFIER);

        assertThatThrownBy(() -> service.create(new CreateLegoSetCommand(
                        "10308",
                        "External",
                        "Icons",
                        1,
                        2026,
                        RetirementStatus.ACTIVE,
                        "https://tracker.example/image.jpg",
                        false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("내부 catalog SVG");
        assertThatThrownBy(() -> service.update(new UpdateLegoSetCommand(
                        "10307",
                        "External",
                        "Icons",
                        1,
                        2026,
                        RetirementStatus.ACTIVE,
                        "/api/v1/media/images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg",
                        false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void legacyExternalCatalogImage_isQuarantinedFromDomainOutput() {
        LegoSet legacy = new LegoSet(
                "10308", "Legacy", "Icons", 1, 2026, RetirementStatus.ACTIVE, "https://tracker.example/pixel.gif");

        assertThat(legacy.getImageUrl()).isNull();
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
