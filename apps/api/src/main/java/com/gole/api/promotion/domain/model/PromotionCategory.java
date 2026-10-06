package com.gole.api.promotion.domain.model;

/** 홍보 글의 종류. 트리거가 정한다 — 릴리스면 FEATURE, 정기 스케줄이면 SERVICE. */
public enum PromotionCategory {
    /** 새로 나간 기능을 알리는 글. 릴리스 하나에서 나온다. */
    FEATURE,
    /** 릴리스와 무관하게 서비스 자체를 소개하는 글. */
    SERVICE
}
