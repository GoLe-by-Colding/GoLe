package com.gole.api.promotion.domain.exception;

import com.gole.api.common.exception.ConflictException;
import java.time.Instant;

/** 직전 발행 후 최소 간격이 지나지 않았을 때. 피드가 한꺼번에 채워지지 않게 한다. */
public class PromotionPublishTooSoonException extends ConflictException {

    public PromotionPublishTooSoonException(Instant availableAt) {
        super("PROMOTION_PUBLISH_TOO_SOON", "Next promotion post can be published after " + availableAt);
    }
}
