package com.gole.api.listing.domain.model;

import java.util.Objects;

/**
 * 한 번의 매물 수정에서 일어난 가격 변화. 가격 인하 후속 처리(찜한 사람 알림 등)의 판단 근거다.
 *
 * @param before 수정 전 가격
 * @param after  수정 후 가격
 */
public record PriceChange(Money before, Money after) {

    public PriceChange {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    public boolean dropped() {
        return after.amount() < before.amount();
    }

    public boolean raised() {
        return after.amount() > before.amount();
    }
}
