package com.gole.api.review.application.port.in;

/** Inbound port: 운영 대시보드에 보일 전체 후기 수. */
public interface CountReviewsUseCase {

    /** 전체 후기 수(추정치 — 컬렉션 메타데이터로 센다). */
    long estimatedReviewCount();
}
