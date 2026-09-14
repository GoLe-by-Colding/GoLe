package com.gole.api.brickfilter.application.port.out;

import com.gole.api.brickfilter.domain.model.BrickJob;
import java.time.Instant;
import java.util.*;

public interface BrickLedgerPort {
    record Ledger(String owner, String day, int occupied, int attempts, List<BrickJob> jobs) {}

    Ledger load(String owner, String day);

    boolean reserve(String owner, String day, BrickJob job);

    boolean complete(String owner, String day, String id, Instant now);

    boolean fail(String owner, String day, String id);

    List<Ledger> expired(Instant now);

    void purge(Instant now);
}
