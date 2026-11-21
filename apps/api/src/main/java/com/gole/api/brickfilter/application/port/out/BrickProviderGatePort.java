package com.gole.api.brickfilter.application.port.out;

import java.time.Instant;

/** 분산 제공자 예산. 실패했거나 결과가 불확실한 호출도 예산을 소모한다. */
public interface BrickProviderGatePort {
    boolean acquire(String token, Instant now);

    void release(String token);
}
