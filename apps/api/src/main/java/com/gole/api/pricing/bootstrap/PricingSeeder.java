package com.gole.api.pricing.bootstrap;

import com.gole.api.pricing.adapter.out.persistence.PriceTransactionDocument;
import com.gole.api.pricing.adapter.out.persistence.PriceTransactionMongoRepository;
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

        // 상태별 감가 계수와 시계열 포인트 수(미개봉이 가장 풍부).
        record Band(String condition, double factor, int weeks) {}
        List<Band> bands = List.of(
                new Band("new_sealed", 1.00, 30),
                new Band("used_complete", 0.78, 18),
                new Band("used_incomplete", 0.55, 12));

        Instant now = Instant.now();
        List<PriceTransactionDocument> docs = new ArrayList<>();
        int migrated = 0;

        for (Seed s : seeds) {
            List<PriceTransactionDocument> existing = repository.findBySetNumberOrderByExecutedAtAsc(s.setNumber());
            if (!existing.isEmpty()) {
                boolean tagged = existing.stream().anyMatch(d -> d.getCondition() != null);
                if (tagged) {
                    continue; // 이미 상태 태깅된 데이터 → 보존(멱등)
                }
                // 레거시(상태 미태깅) 시드 → 제거 후 상태별로 재시드.
                repository.deleteAll(existing);
                migrated++;
            }

            int hash = Math.abs(s.setNumber().hashCode());
            double trend = ((hash % 37) - 12) / 100.0; // -12% ~ +24% 장기 추세
            for (Band band : bands) {
                long bandBase = Math.round(s.base() * band.factor());
                for (int week = band.weeks(); week >= 0; week--) {
                    double progress = (double) (band.weeks() - week) / band.weeks();
                    int wobble = ((hash + week * 17 + band.condition().hashCode()) % 13) - 6; // ±6%
                    double f = 1.0 + trend * progress + wobble / 100.0;
                    long price = Math.max(1, Math.round(bandBase * f));
                    Instant executedAt = now.minus(week * 7L, ChronoUnit.DAYS);
                    docs.add(new PriceTransactionDocument(
                            UUID.randomUUID().toString(), s.setNumber(), price, 1, executedAt, band.condition()));
                }
            }
        }

        if (!docs.isEmpty()) {
            repository.saveAll(docs);
            log.info("[seed] pricing: {}건 체결 이력 적재(상태별, 레거시 마이그레이션 {}세트)", docs.size(), migrated);
        }
    }
}
