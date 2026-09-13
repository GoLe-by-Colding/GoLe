package com.gole.api.brickfilter.application.port.in;

import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.brickfilter.domain.model.BrickJob.Status;
import java.time.Instant;
import java.util.List;

public interface ManageBrickFilterUseCase {
    record Quota(String day, int remaining, int limit, boolean enabled, boolean retryAvailable, Instant resetsAt) {}

    record View(String id, Mode mode, Status status, Instant leaseUntil, Instant resultUntil) {}

    Quota quota(String owner);

    View submit(String owner, String id, Mode mode, byte[] input);

    List<View> recent(String owner);

    View get(String owner, String id);

    byte[] result(String owner, String id);
}
