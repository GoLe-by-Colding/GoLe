package com.gole.api.brickfilter.application.port.out;

import java.time.Instant;
import java.util.Optional;

public interface BrickBlobPort {
    void put(String owner, String job, String kind, byte[] content, Instant expiresAt);

    Optional<byte[]> get(String owner, String job, String kind, Instant now);

    void delete(String owner, String job, String kind);

    void purge(Instant now);
}
