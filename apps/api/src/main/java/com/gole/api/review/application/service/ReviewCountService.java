package com.gole.api.review.application.service;

import com.gole.api.review.application.port.in.CountReviewsUseCase;
import com.gole.api.review.application.port.out.ReviewCountPort;
import org.springframework.stereotype.Service;

/** 운영 대시보드용 후기 수. */
@Service
public class ReviewCountService implements CountReviewsUseCase {

    private final ReviewCountPort counts;

    public ReviewCountService(ReviewCountPort counts) {
        this.counts = counts;
    }

    @Override
    public long estimatedReviewCount() {
        return counts.estimatedReviewCount();
    }
}
