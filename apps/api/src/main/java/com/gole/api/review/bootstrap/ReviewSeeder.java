package com.gole.api.review.bootstrap;

import com.gole.api.review.adapter.out.persistence.ReviewMongoRepository;
import com.gole.api.review.application.port.out.ReviewIdGeneratorPort;
import com.gole.api.review.application.port.out.ReviewRepositoryPort;
import com.gole.api.review.domain.model.Review;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 거래 후기 시드 데이터. 컬렉션이 비어 있을 때만 데모 후기를 적재한다(멱등).
 *
 * <p>실거래(주문→완료→후기) 없이도 판매자 평점이 노출되도록, 다른 시더와 동일하게
 * 영속성 포트에 직접 적재한다. orderId/reviewerId는 데모용 합성 식별자다.
 */
@Component
@Order(6)
@ConditionalOnProperty(name = "gole.review.seed-on-empty", havingValue = "true", matchIfMissing = true)
public class ReviewSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewSeeder.class);

    private final ReviewRepositoryPort reviewRepository;
    private final ReviewMongoRepository mongoRepository;
    private final ReviewIdGeneratorPort idGenerator;
    private final Clock clock;

    public ReviewSeeder(
            ReviewRepositoryPort reviewRepository,
            ReviewMongoRepository mongoRepository,
            ReviewIdGeneratorPort idGenerator,
            Clock clock) {
        this.reviewRepository = reviewRepository;
        this.mongoRepository = mongoRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    private record Seed(String seller, String reviewer, int rating, String content) {}

    @Override
    public void run(String... args) {
        if (mongoRepository.count() > 0) {
            return;
        }
        List<Seed> seeds = List.of(
                new Seed("seller-aurora", "buyer-jun", 5, "포장 꼼꼼하고 상태도 설명 그대로였어요. 또 거래하고 싶네요!"),
                new Seed("seller-aurora", "buyer-mina", 5, "미개봉 새상품 정품 확인했습니다. 빠른 배송 감사합니다."),
                new Seed("seller-aurora", "buyer-tae", 4, "전체적으로 만족! 박스 모서리 약간 눌림은 있었지만 가격 대비 좋아요."),
                new Seed("seller-brickbank", "buyer-soo", 5, "부품 누락 없이 완벽했어요. 설명서까지 깨끗합니다."),
                new Seed("seller-brickbank", "buyer-hyun", 4, "응답 빠르고 친절하셨어요. 직거래로 안전하게 받았습니다."),
                new Seed("seller-minifig", "buyer-ji", 5, "미니피그 상태 최고예요. 희귀 피그 구해서 너무 만족합니다."),
                new Seed("seller-minifig", "buyer-won", 4, "사진과 동일한 상태라 안심하고 거래했어요."));

        Instant now = Instant.now(clock);
        int i = 0;
        for (Seed s : seeds) {
            // 데모용: 최근 며칠에 걸쳐 분산된 작성 시각
            Instant createdAt = now.minus((seeds.size() - i) * 9L, ChronoUnit.HOURS);
            Review review = Review.write(
                    idGenerator.newId(),
                    "seed-order-" + i,
                    s.reviewer(),
                    s.seller(),
                    s.rating(),
                    s.content(),
                    createdAt);
            reviewRepository.save(review);
            i++;
        }
        log.info("[seed] review: {}개 데모 후기 적재", seeds.size());
    }
}
