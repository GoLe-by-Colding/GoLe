package com.gole.api.brickfilter.domain.model;

import java.time.Instant;

public record BrickJob(String id, Mode mode, String digest, Status status, Instant leaseUntil, Instant resultUntil) {
    public enum Mode {
        MINIFIGURE,
        BRICK_OBJECT
    }

    public enum Status {
        RESERVED,
        SUCCEEDED,
        FAILED
    }
}
