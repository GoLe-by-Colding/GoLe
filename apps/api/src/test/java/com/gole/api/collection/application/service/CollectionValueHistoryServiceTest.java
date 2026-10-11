package com.gole.api.collection.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.collection.application.port.in.RecordCollectionValueSnapshotsUseCase.RecordSummary;
import com.gole.api.collection.application.port.out.CollectionValueSnapshotRepositoryPort;
import com.gole.api.collection.application.port.out.LatestPriceProviderPort;
import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 컬렉션 자산 추이: 조회 시 오늘 점 갱신과 하루치 일괄 기록. (collection-value-history H1~H5) */
class CollectionValueHistoryServiceTest {

    /** 2026-03-10 23:30 UTC = 서울 2026-03-11 08:30. 날짜는 서울 기준으로 갈려야 한다. */
    private static final Instant NOW = Instant.parse("2026-03-10T23:30:00Z");

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 11);

    private InMemoryCollectionRepository collections;
    private InMemorySnapshots snapshots;
    private Map<String, Long> priceTable;
    private AtomicInteger priceLookups;
    private CollectionValueSnapshotProperties properties;
    private CollectionValueHistoryService service;

    @BeforeEach
    void setUp() {
        collections = new InMemoryCollectionRepository();
        snapshots = new InMemorySnapshots();
        priceTable = new HashMap<>(Map.of("10307", 280_000L, "75313", 600_000L));
        priceLookups = new AtomicInteger();
        LatestPriceProviderPort prices = setNumber -> {
            priceLookups.incrementAndGet();
            return Optional.ofNullable(priceTable.get(setNumber));
        };
        properties = new CollectionValueSnapshotProperties();
        service = new CollectionValueHistoryService(
                collections, prices, snapshots, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void own(String userId, String setNumber) {
        collections.save(new CollectionItem(
                userId + "-" + setNumber + "-" + collections.store.size(),
                userId,
                setNumber,
                OwnershipStatus.OWNED,
                NOW));
    }

    private void want(String userId, String setNumber) {
        collections.save(new CollectionItem(
                userId + "-w-" + collections.store.size(), userId, setNumber, OwnershipStatus.WANTED, NOW));
    }

    private static CollectionValueSnapshot point(String userId, LocalDate date, long value) {
        return new CollectionValueSnapshot(userId, date, value, 1, value > 0 ? 1 : 0, NOW.minusSeconds(86_400));
    }

    @Test
    @DisplayName("조회하면 오늘(서울 기준) 점을 먼저 갱신해 지난 기록 뒤에 붙인다")
    void history_refreshesTodayAndAppendsAfterStoredPoints() {
        own("u1", "10307");
        own("u1", "99999"); // 시세 없음 — 값은 0, 보유 수에는 든다
        snapshots.upsert(point("u1", TODAY.minusDays(2), 250_000));
        snapshots.upsert(point("u1", TODAY.minusDays(1), 260_000));

        List<CollectionValueSnapshot> points = service.history("u1", 90);

        assertThat(points)
                .extracting(CollectionValueSnapshot::date)
                .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        CollectionValueSnapshot today = points.getLast();
        assertThat(today.ownedValue()).isEqualTo(280_000L);
        assertThat(today.ownedCount()).isEqualTo(2);
        assertThat(today.pricedCount()).isEqualTo(1);
        assertThat(today.capturedAt()).isEqualTo(NOW);
        assertThat(snapshots.find("u1", TODAY)).contains(today);
    }

    @Test
    @DisplayName("오늘 점이 이미 있으면 덮어쓰고 중복으로 내보내지 않는다")
    void history_replacesStaleTodayPoint() {
        own("u1", "75313");
        snapshots.upsert(point("u1", TODAY, 1_000));

        List<CollectionValueSnapshot> points = service.history("u1", 30);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().ownedValue()).isEqualTo(600_000L);
        assertThat(snapshots.all()).hasSize(1);
    }

    @Test
    @DisplayName("보유 항목도 지난 기록도 없는 방문자는 0짜리 문서를 남기지 않는다")
    void history_skipsEmptyVisitorWithoutHistory() {
        want("u1", "10307"); // 위시만 있음

        assertThat(service.history("u1", 90)).isEmpty();
        assertThat(snapshots.all()).isEmpty();
    }

    @Test
    @DisplayName("기록이 있던 사용자가 다 팔았으면 0도 추이로 남긴다")
    void history_recordsZeroWhenUserHadHistory() {
        snapshots.upsert(point("u1", TODAY.minusDays(1), 280_000));

        List<CollectionValueSnapshot> points = service.history("u1", 90);

        assertThat(points).hasSize(2);
        assertThat(points.getLast().ownedValue()).isZero();
        assertThat(points.getLast().ownedCount()).isZero();
    }

    @Test
    @DisplayName("기간 밖의 기록은 돌려주지 않는다 — days는 오늘을 포함한 일수다")
    void history_limitsRangeToDaysIncludingToday() {
        own("u1", "10307");
        snapshots.upsert(point("u1", TODAY.minusDays(7), 100)); // 8일 전 — 7일 창 밖
        snapshots.upsert(point("u1", TODAY.minusDays(6), 200)); // 7일 창의 첫날

        List<CollectionValueSnapshot> points = service.history("u1", 7);

        assertThat(points).extracting(CollectionValueSnapshot::date).containsExactly(TODAY.minusDays(6), TODAY);
    }

    @Test
    @DisplayName("오늘 갱신이 실패해도 저장된 기록은 보여 준다")
    void history_returnsStoredPointsWhenRefreshFails() {
        snapshots.upsert(point("u1", TODAY.minusDays(1), 280_000));
        collections.failFindByUser("u1");

        assertThat(service.history("u1", 90))
                .extracting(CollectionValueSnapshot::date)
                .containsExactly(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("조회 기간은 1~365일만 받는다")
    void history_rejectsOutOfRangeDays() {
        assertThatThrownBy(() -> service.history("u1", 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.history("u1", 366)).isInstanceOf(BadRequestException.class);
        assertThat(service.history("u1", 1)).isEmpty();
        assertThat(service.history("u1", 365)).isEmpty();
    }

    @Test
    @DisplayName("일괄 기록은 커서로 모든 보유 사용자를 찍고 한 명의 실패가 나머지를 막지 않는다")
    void recordAll_pagesThroughOwnersAndIsolatesFailures() {
        properties.setPageSize(2);
        own("a", "10307");
        own("b", "75313");
        own("c", "10307");
        own("d", "99999");
        own("e", "10307");
        want("f", "10307"); // 보유가 아니면 대상이 아니다
        collections.failFindByUser("c");

        RecordSummary summary = service.recordAll(NOW);

        assertThat(summary).isEqualTo(new RecordSummary(5, 4, 1));
        assertThat(collections.cursors).containsExactly(null, "b", "d");
        assertThat(snapshots.all()).extracting(CollectionValueSnapshot::userId).containsExactly("a", "b", "d", "e");
        assertThat(snapshots.find("b", TODAY).orElseThrow().ownedValue()).isEqualTo(600_000L);
        assertThat(snapshots.find("d", TODAY).orElseThrow().pricedCount()).isZero();
    }

    @Test
    @DisplayName("같은 날 다시 돌려도 사용자당 한 점이고, 한 실행 안에서는 세트 시세를 한 번만 묻는다")
    void recordAll_isIdempotentPerDayAndMemoizesPrices() {
        own("a", "10307");
        own("b", "10307");
        own("c", "10307");

        service.recordAll(NOW);
        assertThat(priceLookups.get()).isEqualTo(1);

        priceTable.put("10307", 300_000L);
        service.recordAll(NOW.plusSeconds(3_600));

        assertThat(snapshots.all()).hasSize(3);
        assertThat(snapshots.all()).allSatisfy(point -> {
            assertThat(point.date()).isEqualTo(TODAY);
            assertThat(point.ownedValue()).isEqualTo(300_000L);
        });
    }

    @Test
    @DisplayName("대상 조회가 실패하면 그 실행을 접고 실패로 센다")
    void recordAll_stopsWhenOwnerLookupFails() {
        CollectionValueHistoryService broken = new CollectionValueHistoryService(
                new InMemoryCollectionRepository() {
                    @Override
                    public List<String> findUserIdsWithStatus(OwnershipStatus status, String afterUserId, int limit) {
                        throw new IllegalStateException("aggregate failed");
                    }
                },
                setNumber -> Optional.empty(),
                snapshots,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(broken.recordAll(NOW)).isEqualTo(new RecordSummary(0, 0, 1));
        assertThat(snapshots.all()).isEmpty();
    }

    /** 사용자·날짜 기준 덮어쓰기 저장소. 실제 어댑터의 유일 키 {@code {userId, date}}와 같다. */
    private static final class InMemorySnapshots implements CollectionValueSnapshotRepositoryPort {

        private final Map<String, CollectionValueSnapshot> byKey = new HashMap<>();

        @Override
        public void upsert(CollectionValueSnapshot snapshot) {
            byKey.put(snapshot.userId() + "|" + snapshot.date(), snapshot);
        }

        @Override
        public List<CollectionValueSnapshot> findRange(String userId, LocalDate from, LocalDate to) {
            return byKey.values().stream()
                    .filter(s -> s.userId().equals(userId))
                    .filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to))
                    .sorted(Comparator.comparing(CollectionValueSnapshot::date))
                    .toList();
        }

        Optional<CollectionValueSnapshot> find(String userId, LocalDate date) {
            return Optional.ofNullable(byKey.get(userId + "|" + date));
        }

        List<CollectionValueSnapshot> all() {
            List<CollectionValueSnapshot> all = new ArrayList<>(byKey.values());
            all.sort(
                    Comparator.comparing(CollectionValueSnapshot::userId).thenComparing(CollectionValueSnapshot::date));
            return all;
        }
    }
}
