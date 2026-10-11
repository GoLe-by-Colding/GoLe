package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.in.GetCollectionValueHistoryUseCase;
import com.gole.api.collection.application.port.in.RecordCollectionValueSnapshotsUseCase;
import com.gole.api.collection.application.port.out.CollectionRepositoryPort;
import com.gole.api.collection.application.port.out.CollectionValueSnapshotRepositoryPort;
import com.gole.api.collection.application.port.out.LatestPriceProviderPort;
import com.gole.api.collection.domain.model.CollectionValuation;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 컬렉션 자산 추이: 하루치 스냅샷 기록과 조회. (collection-value-history H1~H5)
 *
 * <p>값 산출은 현재 추정가와 같은 {@link CollectionValuation#of}를 쓴다 — 그래프 끝점과 "보유 추정가" 카드가
 * 어긋나지 않게 하기 위해서다.
 */
@Service
public class CollectionValueHistoryService
        implements GetCollectionValueHistoryUseCase, RecordCollectionValueSnapshotsUseCase {

    private static final Logger log = LoggerFactory.getLogger(CollectionValueHistoryService.class);

    private final CollectionRepositoryPort collections;
    private final LatestPriceProviderPort latestPrices;
    private final CollectionValueSnapshotRepositoryPort snapshots;
    private final CollectionValueSnapshotProperties properties;
    private final Clock clock;

    public CollectionValueHistoryService(
            CollectionRepositoryPort collections,
            LatestPriceProviderPort latestPrices,
            CollectionValueSnapshotRepositoryPort snapshots,
            CollectionValueSnapshotProperties properties,
            Clock clock) {
        this.collections = collections;
        this.latestPrices = latestPrices;
        this.snapshots = snapshots;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<CollectionValueSnapshot> history(String userId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new BadRequestException("INVALID_PARAMETER", "조회 기간(days)은 1~" + MAX_DAYS + "일이어야 합니다");
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, properties.zoneId());
        List<CollectionValueSnapshot> stored = snapshots.findRange(userId, today.minusDays(days - 1L), today);

        // H4: 스케줄러가 돌기 전에 들어온 사용자도 오늘 점을 바로 본다. 갱신이 실패해도 지난 기록은 보여 준다.
        Optional<CollectionValueSnapshot> refreshed = refreshToday(userId, today, now, !stored.isEmpty());
        if (refreshed.isEmpty()) {
            return stored;
        }
        List<CollectionValueSnapshot> points = new ArrayList<>(stored.size() + 1);
        stored.stream().filter(point -> !point.date().equals(today)).forEach(points::add);
        points.add(refreshed.orElseThrow());
        return List.copyOf(points);
    }

    /**
     * 보유 항목이 없고 지난 기록도 없는 사용자는 찍지 않는다. 컬렉션을 한 번도 채운 적 없는 방문자마다 0짜리 문서를
     * 쌓을 이유가 없다. 반대로 기록이 있으면 0이라도 남긴다 — 다 팔았다는 것도 추이다.
     */
    private Optional<CollectionValueSnapshot> refreshToday(
            String userId, LocalDate today, Instant now, boolean hasHistory) {
        try {
            CollectionValuation valuation =
                    CollectionValuation.of(collections.findByUser(userId), latestPrices::latestPrice);
            if (valuation.ownedCount() == 0 && !hasHistory) {
                return Optional.empty();
            }
            CollectionValueSnapshot snapshot = CollectionValueSnapshot.capture(userId, today, valuation, now);
            snapshots.upsert(snapshot);
            return Optional.of(snapshot);
        } catch (RuntimeException failure) {
            log.warn("컬렉션 오늘 스냅샷 갱신 실패 — 저장된 기록만 돌려준다 userId={}", userId, failure);
            return Optional.empty();
        }
    }

    @Override
    public RecordSummary recordAll(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, properties.zoneId());
        // 같은 세트를 가진 사용자가 많다. 한 번의 실행 안에서는 세트별 시세를 한 번만 묻는다.
        Function<String, Optional<Long>> prices = memoized(latestPrices::latestPrice);
        int pageSize = properties.pageSize();
        int targets = 0;
        int succeeded = 0;
        int failed = 0;
        String cursor = null;
        while (true) {
            List<String> page;
            try {
                page = collections.findUserIdsWithStatus(OwnershipStatus.OWNED, cursor, pageSize);
            } catch (RuntimeException failure) {
                // 대상 조회가 막히면 이번 실행은 여기서 접는다. 다음 실행이 같은 날이면 덮어쓰므로 다시 돌려도 된다.
                log.warn("[collection-value-snapshot] 대상 사용자 조회 실패 date={} after={}", date, cursor, failure);
                failed++;
                break;
            }
            for (String userId : page) {
                targets++;
                try {
                    CollectionValuation valuation = CollectionValuation.of(collections.findByUser(userId), prices);
                    snapshots.upsert(CollectionValueSnapshot.capture(userId, date, valuation, now));
                    succeeded++;
                } catch (RuntimeException failure) {
                    failed++;
                    log.warn("[collection-value-snapshot] 사용자 스냅샷 실패 date={} userId={}", date, userId, failure);
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            cursor = page.getLast();
        }
        return new RecordSummary(targets, succeeded, failed);
    }

    /** 실패는 기억하지 않는다 — 한 세트의 일시 장애가 같은 실행의 다른 사용자까지 0으로 만들지 않게. */
    private static Function<String, Optional<Long>> memoized(Function<String, Optional<Long>> lookup) {
        Map<String, Optional<Long>> cache = new HashMap<>();
        return setNumber -> {
            Optional<Long> cached = cache.get(setNumber);
            if (cached != null) {
                return cached;
            }
            Optional<Long> price = lookup.apply(setNumber);
            cache.put(setNumber, price == null ? Optional.empty() : price);
            return price;
        };
    }
}
