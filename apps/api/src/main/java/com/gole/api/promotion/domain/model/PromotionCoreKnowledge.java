package com.gole.api.promotion.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 브랜드 정본에서 검토한 초기 지식. 등록은 제안이며 기존 지식은 덮어쓰지 않는다. */
public final class PromotionCoreKnowledge {
    private PromotionCoreKnowledge() {}

    public static List<PromotionGuideline> proposals(String actorId, Instant now) {
        return List.of(
                proposal(
                        "brand-colors", "GoLe의 브랜드 색은 Royal Blue #1D4ED8이고 포인트 색은 Brick Gold #EAB308이다.", actorId, now),
                proposal("whale-mark", "승인된 GoLe 마크는 얼굴이 왼쪽, 꼬리가 오른쪽인 옆모습 브릭 고래다. 정면 고래는 승인된 마크가 아니다.", actorId, now),
                proposal(
                        "brand-concept",
                        "GoLe는 고래와 발음이 통하며 브랜드 컨셉은 고래와 브릭의 결합이다. 바다의 파랑과 브릭의 노랑이 브랜드 색의 근거다.",
                        actorId,
                        now));
    }

    private static PromotionGuideline proposal(String key, String content, String actorId, Instant now) {
        String id = UUID.nameUUIDFromBytes(("gole:promotion-core-v1:" + key).getBytes(StandardCharsets.UTF_8))
                .toString();
        return new PromotionGuideline(
                id,
                PromotionGuidelineKind.KNOWLEDGE,
                content,
                List.of(PromotionMemoryTarget.values()),
                List.of(PromotionCategory.values()),
                List.of(),
                PromotionGuidelineStatus.PROPOSED,
                actorId,
                now,
                now,
                null,
                null,
                "seed-core-v1",
                0,
                PromotionGuidelineSourceType.CORE_SEED,
                ".kiro/steering/brand-identity.md@promotion-core-v1");
    }
}
