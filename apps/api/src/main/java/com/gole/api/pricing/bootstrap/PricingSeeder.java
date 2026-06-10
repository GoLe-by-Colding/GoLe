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
        // setNumber → 기준가(원). 기준가에 추세와 결정적 노이즈를 더해 시계열을 만든다.
        record Seed(String setNumber, long base) {
        }
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

        Instant now = Instant.now();
        List<PriceTransactionDocument> docs = new ArrayList<>();
        int weeks = 30;
        for (Seed s : seeds) {
            // 세트별 멱등: 이미 체결 이력이 있으면 건너뛴다(기존 데이터 보존).
            if (!repository.findBySetNumberOrderByExecutedAtAsc(s.setNumber()).isEmpty()) {
                continue;
            }
            int hash = Math.abs(s.setNumber().hashCode());
            // 세트별 장기 추세: -12% ~ +24%
            double trend = ((hash % 37) - 12) / 100.0;
            for (int week = weeks; week >= 0; week--) {
                double progress = (double) (weeks - week) / weeks; // 0 → 1
                // 결정적 단기 변동(±6%).
                int wobble = ((hash + week * 17) % 13) - 6;
                double factor = 1.0 + trend * progress + wobble / 100.0;
                long price = Math.max(1, Math.round(s.base() * factor));
                Instant executedAt = now.minus(week * 7L, ChronoUnit.DAYS);
                docs.add(new PriceTransactionDocument(
                        UUID.randomUUID().toString(), s.setNumber(), price, 1, executedAt));
            }
        }
        if (!docs.isEmpty()) {
            repository.saveAll(docs);
            log.info("[seed] pricing: {}건 체결 이력 적재", docs.size());
        }
    }
}
