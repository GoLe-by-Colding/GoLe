package com.gole.api.community.domain.model;

/**
 * 게시글 주제. 자랑/창작(MOC)/리뷰/질문/팁/이스터에그/자유 등 다양한 공유·토론을 지원한다.
 * 레거시 호환을 위해 GENERAL/MOC 값을 유지한다(저장 문자열 그대로 매핑).
 */
public enum PostType {
    GENERAL,
    SHOWCASE,
    MOC,
    REVIEW,
    QUESTION,
    TIP,
    EASTER_EGG;

    /** 외부 노출 키(소문자). 프론트 토픽 키와 일치. */
    public String key() {
        return name().toLowerCase();
    }

    /** 키 → enum. null/미상은 GENERAL(자유)로 간주. */
    public static PostType fromKey(String key) {
        if (key == null) {
            return GENERAL;
        }
        for (PostType t : values()) {
            if (t.key().equalsIgnoreCase(key) || t.name().equalsIgnoreCase(key)) {
                return t;
            }
        }
        return GENERAL;
    }
}
