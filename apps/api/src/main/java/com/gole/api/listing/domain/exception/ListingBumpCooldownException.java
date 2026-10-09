package com.gole.api.listing.domain.exception;

import com.gole.api.common.exception.TooManyRequestsException;
import java.time.Duration;

/** 끌올 쿨다운이 끝나지 않았다(429 + Retry-After). (listing-edit-and-bump B2) */
public class ListingBumpCooldownException extends TooManyRequestsException {

    public ListingBumpCooldownException(Duration retryAfter) {
        super("LISTING_BUMP_COOLDOWN", "아직 끌올할 수 없습니다. 잠시 후 다시 시도해 주세요", retryAfter);
    }
}
