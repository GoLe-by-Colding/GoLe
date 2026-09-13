package com.gole.api.brickfilter.application.port.out;

import java.time.Instant;

/** Distributed provider budget. Failed/uncertain calls still consume this budget. */
public interface BrickProviderGatePort {
    boolean acquire(String token, Instant now);

    void release(String token);
}
