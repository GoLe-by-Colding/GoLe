package com.gole.api.shipping.domain.model;

import java.util.Locale;

/**
 * 정규화된 배송 상태. (R2.2)
 *
 * <p>택배사별 원문 상태는 {@code Shipment.rawStatus}에 별도 보존하고,
 * 도메인 전이는 이 네 값으로만 판단한다.
 */
public enum DeliveryStatus {
    /** 송장은 등록됐지만 택배사가 아직 접수하지 않음. */
    PENDING(0),
    /** 이동 중(집화 완료 포함). */
    IN_TRANSIT(1),
    /** 배송 완료. 종결 상태 — 이후 어떤 전이도 없다. */
    DELIVERED(2),
    /** 조회 불가. 상태를 전진시키지 않으며 별도로 지속 시간만 추적한다. */
    UNKNOWN(-1);

    private final int rank;

    DeliveryStatus(int rank) {
        this.rank = rank;
    }

    /**
     * 이 상태가 {@code current}에서의 전진인지. (역행 금지 — 외부 API 흔들림 방어)
     * {@code UNKNOWN}은 어떤 상태에서도 전진이 아니다.
     */
    public boolean advancesFrom(DeliveryStatus current) {
        return this != UNKNOWN && this.rank > current.rank;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
