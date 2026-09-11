package com.gole.api.listing.domain.model;

import com.gole.api.common.exception.BadRequestException;

/** 매물에 지정할 수 있는 관심 테마. account 관심태그 카탈로그와 파리티 테스트로 동기화한다. */
public enum InterestTag {
    STAR_WARS("star-wars", "스타워즈"),
    TECHNIC("technic", "테크닉"),
    CREATOR("creator", "크리에이터"),
    ARCHITECTURE("architecture", "아키텍처"),
    CITY("city", "시티"),
    NINJAGO("ninjago", "닌자고"),
    HARRY_POTTER("harry-potter", "해리포터"),
    IDEAS("ideas", "아이디어"),
    SUPER_HEROES("super-heroes", "슈퍼히어로"),
    FRIENDS("friends", "프렌즈"),
    DUPLO("duplo", "듀플로"),
    ICONS("icons", "아이콘"),
    SPEED_CHAMPIONS("speed-champions", "스피드챔피언"),
    MINECRAFT("minecraft", "마인크래프트");

    private final String key;
    private final String label;

    InterestTag(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** null/blank는 미지정으로, 알 수 없는 키는 잘못된 요청으로 처리한다. */
    public static InterestTag fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        for (InterestTag tag : values()) {
            if (tag.key.equalsIgnoreCase(normalized) || tag.name().equalsIgnoreCase(normalized)) {
                return tag;
            }
        }
        throw new BadRequestException("INVALID_INTEREST_TAG", "선택할 수 없는 관심 테마입니다");
    }
}
