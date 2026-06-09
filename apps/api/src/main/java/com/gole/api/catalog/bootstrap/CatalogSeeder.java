package com.gole.api.catalog.bootstrap;

import com.gole.api.catalog.adapter.out.persistence.LegoSetDocument;
import com.gole.api.catalog.adapter.out.persistence.LegoSetMongoRepository;
import com.gole.api.catalog.domain.model.RetirementStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 시드 데이터. 컬렉션이 비어 있을 때만 실제 LEGO 세트를 적재한다(멱등).
 * {@code gole.catalog.seed-on-empty=false} 로 비활성화(테스트 격리).
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "gole.catalog.seed-on-empty", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final LegoSetMongoRepository repository;

    public CatalogSeeder(LegoSetMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<LegoSetDocument> sets = List.of(
                set("10307", "에펠탑", "Icons", 10001, 2022, RetirementStatus.ACTIVE, true),
                set("75192", "밀레니엄 팰컨 UCS", "Star Wars", 7541, 2017, RetirementStatus.ACTIVE, true),
                set("10294", "타이타닉", "Icons", 9090, 2021, RetirementStatus.ACTIVE, true),
                set("42143", "페라리 데이토나 SP3", "Technic", 3778, 2022, RetirementStatus.ACTIVE, true),
                set("71043", "호그와트 성", "Harry Potter", 6020, 2018, RetirementStatus.ACTIVE, true),
                set("10300", "백 투 더 퓨처 타임머신", "Icons", 1872, 2022, RetirementStatus.ACTIVE, true),
                set("21330", "나 홀로 집에", "Ideas", 3955, 2021, RetirementStatus.ACTIVE, true),
                set("75313", "AT-AT UCS", "Star Wars", 6785, 2021, RetirementStatus.ACTIVE, true),
                set("10276", "콜로세움", "Icons", 9036, 2020, RetirementStatus.RETIRED, false),
                set("21318", "트리하우스", "Ideas", 3036, 2019, RetirementStatus.RETIRED, false),
                set("92176", "NASA 아폴로 새턴 V", "Ideas", 1969, 2020, RetirementStatus.RETIRED, false),
                set("10497", "갤럭시 익스플로러", "Icons", 1254, 2022, RetirementStatus.ACTIVE, false));
        repository.saveAll(sets);
        log.info("[seed] catalog: {}개 LEGO 세트 적재", sets.size());
    }

    private LegoSetDocument set(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            RetirementStatus status,
            boolean featured) {
        // 공식 레고 이미지는 호스팅하지 않는다(IP 안전). 카탈로그 커버는 MediaSeeder가 MinIO에
        // 올린 GoLe 오리지널 커버 아트(고래+브릭)를 가리킨다. 매물 사진은 판매자 직접 촬영만 사용.
        String imageUrl = "/api/v1/media/catalog/" + setNumber + ".svg";
        return new LegoSetDocument(
                setNumber, name, theme, pieceCount, releaseYear, status, imageUrl, featured);
    }
}
