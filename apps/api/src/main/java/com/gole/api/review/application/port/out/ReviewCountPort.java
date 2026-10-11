package com.gole.api.review.application.port.out;

/** Outbound port: 전체 후기 수(추정치). */
public interface ReviewCountPort {

    long estimatedReviewCount();
}
