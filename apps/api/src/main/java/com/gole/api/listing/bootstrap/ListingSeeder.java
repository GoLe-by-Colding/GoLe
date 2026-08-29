package com.gole.api.listing.bootstrap;

import com.gole.api.listing.adapter.out.persistence.ListingMongoRepository;
import com.gole.api.listing.application.port.in.CreateListingUseCase;
import com.gole.api.listing.application.port.in.CreateListingUseCase.CreateListingCommand;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 리스팅 시드 데이터. 컬렉션이 비어 있을 때만 데모 매물을 등록한다(멱등).
 * 카탈로그 세트 번호를 참조하며, 인바운드 유스케이스를 통해 도메인 규칙을 거쳐 생성한다.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "gole.listing.seed-on-empty", havingValue = "true")
public class ListingSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ListingSeeder.class);

    private final CreateListingUseCase createListing;
    private final ListingMongoRepository repository;

    public ListingSeeder(CreateListingUseCase createListing, ListingMongoRepository repository) {
        this.createListing = createListing;
        this.repository = repository;
    }

    private static List<String> photos(String setNumber) {
        // 데모 매물 커버는 카탈로그와 동일한 GoLe 오리지널 커버(MinIO)를 재사용한다.
        // 실서비스 매물 사진은 판매자가 직접 촬영한 것만 사용한다(ip-safe-content R3).
        return List.of("/api/v1/media/catalog/" + setNumber + ".svg");
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<CreateListingCommand> demo = List.of(
                listing(
                        "seller-aurora",
                        "에펠탑 미개봉 새상품",
                        "10307 에펠탑 풀박스 미개봉입니다. 보관 깔끔합니다.",
                        890_000,
                        ItemCondition.NEW_SEALED,
                        "10307"),
                listing(
                        "seller-aurora",
                        "밀레니엄 팰컨 UCS 정품",
                        "75192 조립 후 전시만 했습니다. 부품 누락 없음.",
                        1_250_000,
                        ItemCondition.LIKE_NEW,
                        "75192"),
                listing(
                        "seller-brickbank",
                        "타이타닉 미개봉",
                        "10294 타이타닉 새상품, 영수증 포함.",
                        780_000,
                        ItemCondition.NEW_SEALED,
                        "10294"),
                listing(
                        "seller-brickbank",
                        "페라리 데이토나 SP3",
                        "42143 조립완성품, 설명서/박스 보관.",
                        430_000,
                        ItemCondition.USED_GOOD,
                        "42143"),
                listing(
                        "seller-minifig",
                        "호그와트 성 일부 부품",
                        "71043 일부 미니피겨 분실, 본체는 완전.",
                        520_000,
                        ItemCondition.USED_FAIR,
                        "71043"),
                listing(
                        "seller-minifig",
                        "타이타닉 하자 있는 전시품",
                        "10294 운반 중 일부 파손과 변색이 있습니다. 사진 확인 후 문의 주세요.",
                        430_000,
                        ItemCondition.DAMAGED,
                        "10294"),
                listing(
                        "seller-minifig",
                        "백 투 더 퓨처 타임머신 새상품",
                        "10300 미개봉 새상품입니다.",
                        180_000,
                        ItemCondition.NEW_SEALED,
                        "10300"),
                listing("seller-aurora", "나 홀로 집에 미개봉", "21330 풀박스 미개봉.", 330_000, ItemCondition.NEW_SEALED, "21330"),
                listing(
                        "seller-brickbank",
                        "AT-AT UCS 조립완성",
                        "75313 전시품, 상태 최상.",
                        980_000,
                        ItemCondition.LIKE_NEW,
                        "75313"),
                listing(
                        "seller-minifig",
                        "콜로세움 단종품 미개봉",
                        "10276 단종된 콜로세움 새상품.",
                        650_000,
                        ItemCondition.NEW_SEALED,
                        "10276"),
                listing(
                        "seller-aurora",
                        "갤럭시 익스플로러 새상품",
                        "10497 미개봉 새상품.",
                        150_000,
                        ItemCondition.NEW_SEALED,
                        "10497"));

        demo.forEach(createListing::create);
        log.info("[seed] listing: {}개 데모 매물 등록", demo.size());
    }

    private CreateListingCommand listing(
            String sellerId, String title, String description, long price, ItemCondition condition, String setNumber) {
        return new CreateListingCommand(
                sellerId, title, description, price, condition, disclosureFor(condition), photos(setNumber), setNumber);
    }

    /** 상태 등급에 따라 현실감 있는 고지(구성/박스/설명서/누락/하자)를 생성한다. */
    private static ConditionDisclosure disclosureFor(ItemCondition condition) {
        return switch (condition) {
            case NEW_SEALED -> new ConditionDisclosure(Completeness.FULL_BOX, true, true, false, "", "");
            case LIKE_NEW -> new ConditionDisclosure(
                    Completeness.FULL_BOX, true, true, false, "", "조립 후 전시만 한 상태로 미세한 사용감이 있습니다.");
            case USED_GOOD -> new ConditionDisclosure(
                    Completeness.NO_BOX, false, true, false, "", "박스는 없지만 부품과 설명서는 온전합니다.");
            case USED_FAIR -> new ConditionDisclosure(
                    Completeness.BULK, false, false, true, "미니피겨 액세서리 일부와 1x1 타일 약 5개 누락.", "일부 피스에 사용감이 있습니다.");
            case DAMAGED -> new ConditionDisclosure(
                    Completeness.NO_BOX, false, false, true, "운반 중 파손된 조각 3개 누락.", "일부 피스에 변색과 파손이 있습니다.");
        };
    }
}
