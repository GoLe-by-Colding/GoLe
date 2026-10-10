package com.gole.api.shipping.domain.model;

import java.time.Instant;

public record TrackerDiagnostics(
        boolean enabled,
        boolean configured,
        boolean connected,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastFailure) {}
