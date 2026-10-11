package com.gole.api.media.domain.model;

/** 사용자 미디어를 소유하는 공개 콘텐츠 종류. */
public enum MediaTargetType {
    LISTING,
    COMMUNITY_POST,
    PROMOTION_POST,
    /** 관리자가 올린 사이트 마스코트 이미지(mascot-assets R2). */
    MASCOT_ASSET
}
