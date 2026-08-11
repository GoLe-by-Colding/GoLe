package com.gole.api.pricing.bootstrap;

import com.gole.api.pricing.adapter.out.persistence.PriceTransactionDocument;
import com.gole.api.pricing.adapter.out.persistence.PriceTransactionMongoRepository;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 시세 시드 데이터. 컬렉션이 비어 있을 때만 인기 세트의 체결 이력을 적재한다(멱등).
 * 최근 약 24주에 걸쳐 완만한 변동을 갖는 거래를 생성해 시세 차트가 의미를 갖게 한다.
 *
 * <p>이미 체결 이력이 있으면 <b>지우지 않는다</b>. 3단계 시절 키(used_complete/used_incomplete)만
 * 새 등급 키로 바꿔 준다. 실거래는 시세의 원천이라 시더가 임의로 날려서는 안 된다.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "gole.pricing.seed-on-empty", havingValue = "true", matchIfMissing = true)
public class PricingSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PricingSeeder.class);

    private final PriceTransactionMongoRepository repository;

    public PricingSeeder(PriceTransactionMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        // setNumber → 미개봉 기준가(원).
        record Seed(String setNumber, long base) {}
        List<Seed> seeds = List.of(
                new Seed("10307", 850_000),
                new Seed("75192", 1_200_000),
                new Seed("10294", 800_000),
                new Seed("42143", 480_000),
                new Seed("71043", 560_000),
                new Seed("10300", 190_000),
                new Seed("21330", 360_000),
                new Seed("75313", 950_000),
                new Seed("10276", 680_000),
                new Seed("21318", 520_000),
                new Seed("92176", 220_000),
                new Seed("10497", 160_000));

        // 등급별 시계열 포인트 수(weeks + 1건). 감가 계수는 SetCondition이 들고 있다.
        //
        // LIKE_NEW·DAMAGED를 일부러 얇게 둔다. 실제 시장에서도 거의새것·하자품은 체결이 드물고,
        // 등급 표본이 모자랄 때 그룹 표본으로 받치는 경로(basis=group)를 로컬에서 바로 볼 수 있다.
        record Band(SetCondition condition, int weeks) {}
        List<Band> bands = List.of(
                new Band(SetCondition.NEW_SEALED, 30), // 31건 → basis=grade
                new Band(SetCondition.LIKE_NEW, 1), //     2건 → basis=group (COMPLETE 그룹이 받침)
                new Band(SetCondition.USED_GOOD, 18), //  19건 → basis=grade
                new Band(SetCondition.USED_FAIR, 12), //  13건 → basis=grade
                new Band(SetCondition.DAMAGED, 0)); //     1건 → basis=group (INCOMPLETE 그룹이 받침)

        Instant now = Instant.now();
        List<PriceTransactionDocument> docs = new ArrayList<>();
        int remappedCount = 0;
        int purged = 0;

        for (Seed s : seeds) {
            List<PriceTransactionDocument> existing = repository.findBySetNumberOrderByExecutedAtAsc(s.setNumber());
            if (!existing.isEmpty()) {
                remappedCount += remapLegacyKeys(existing);

                boolean tagged = existing.stream().anyMatch(d -> d.getCondition() != null);
                if (tagged) {
                    continue; // 상태 태깅된 데이터 → 보존(멱등)
                }
                // 상태 미태깅 레거시 시드 → 제거 후 등급별로 재시드.
                repository.deleteAll(existing);
                purged++;
            }

            int hash = Math.abs(s.setNumber().hashCode());
            double trend = ((hash % 37) - 12) / 100.0; // -12% ~ +24% 장기 추세
            for (Band band : bands) {
                long bandBase = Math.round(s.base() * band.condition().factor());
                for (int week = band.weeks(); week >= 0; week--) {
                    // weeks가 0이면(단일 포인트) 추세를 적용할 구간 자체가 없다.
                    double progress = band.weeks() == 0 ? 0.0 : (double) (band.weeks() - week) / band.weeks();
                    int wobble = ((hash + week * 17 + band.condition().key().hashCode()) % 13) - 6; // ±6%
                    double f = 1.0 + trend * progress + wobble / 100.0;
                    long price = Math.max(1, Math.round(bandBase * f));
                    Instant executedAt = now.minus(week * 7L, ChronoUnit.DAYS);
                    docs.add(new PriceTransactionDocument(
                            UUID.randomUUID().toString(),
                            s.setNumber(),
                            price,
                            1,
                            executedAt,
                            band.condition().key()));
                }
            }
        }

        if (!docs.isEmpty()) {
            repository.saveAll(docs);
        }
        if (!docs.isEmpty() || remappedCount > 0) {
            log.info("[seed] pricing: {}건 적재(등급별), 레거시 키 {}건 재매핑, 미태깅 {}세트 재시드", docs.size(), remappedCount, purged);
        }
    }

    /**
     * 3단계 시절 키를 새 등급 키로 바꿔 저장한다(문서 id 유지 → 덮어쓰기).
     *
     * <p>읽기 경로는 {@code SetCondition.fromKey}가 레거시를 흡수하므로 이 변환이 없어도
     * 동작은 한다. 다만 저장값이 계속 옛 키로 남으면 인덱스·집계·운영 조회가 두 벌이 되므로
     * 시더가 지나가는 김에 정리한다.
     *
     * @return 재매핑한 문서 수
     */
    private int remapLegacyKeys(List<PriceTransactionDocument> existing) {
        List<PriceTransactionDocument> remapped = existing.stream()
                .filter(d -> isLegacyKey(d.getCondition()))
                .map(d -> new PriceTransactionDocument(
                        d.getId(),
                        d.getSetNumber(),
                        d.getPrice(),
                        d.getQuantity(),
                        d.getExecutedAt(),
                        SetCondition.fromKey(d.getCondition()).key()))
                .toList();
        if (remapped.isEmpty()) {
            return 0;
        }
        repository.saveAll(remapped);
        return remapped.size();
    }

    /** 저장된 키가 정규 키와 다르면 레거시다. 레거시 목록을 여기 또 적지 않기 위한 판별. */
    private static boolean isLegacyKey(String stored) {
        return stored != null
                && !stored.isBlank()
                && !stored.equals(SetCondition.fromKey(stored).key());
    }
}
