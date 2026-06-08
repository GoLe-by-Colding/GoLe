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
        if (repository.count() > 0) {
            return;
        }
        // setNumber → 기준가(원). 기준가 주변으로 변동시킨다.
        record Seed(String setNumber, long base) {
        }
        List<Seed> seeds = List.of(
                new Seed("10307", 850_000),
                new Seed("75192", 1_200_000),
                new Seed("10294", 800_000),
                new Seed("10276", 620_000),
                new Seed("75313", 950_000));

        Instant now = Instant.now();
        List<PriceTransactionDocument> docs = new ArrayList<>();
        for (Seed s : seeds) {
            for (int week = 24; week >= 0; week--) {
                // 결정적 의사난수(±12%) — 시드 재현성을 위해 해시 기반.
                int wobble = ((s.setNumber().hashCode() + week * 31) % 25) - 12; // -12..+12
                long price = s.base() + (s.base() * wobble / 100);
                Instant executedAt = now.minus(week * 7L, ChronoUnit.DAYS);
                docs.add(new PriceTransactionDocument(
                        UUID.randomUUID().toString(), s.setNumber(), price, 1, executedAt));
            }
        }
        repository.saveAll(docs);
        log.info("[seed] pricing: {}건 체결 이력 적재(세트 {}개)", docs.size(), seeds.size());
    }
}
