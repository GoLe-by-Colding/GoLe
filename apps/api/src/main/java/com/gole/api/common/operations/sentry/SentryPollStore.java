package com.gole.api.common.operations.sentry;

import java.time.Instant;
import java.util.List;

/** 조회 진행점과 개인정보 없는 전송 원장. 외부 수락 전에는 완료를 기록하지 않는다. */
public interface SentryPollStore {
    record Scan(Instant from, Instant until, String cursor, boolean readComplete) {}

    record Lease(String owner, Scan scan, Instant readNotBefore) {}

    record Alert(String id, Instant occurredAt, int attempts) {}

    Lease acquire(Instant now);

    void saveScan(Lease lease, Scan scan);

    void deferRead(Lease lease, Instant until);

    void enqueue(String key, Instant occurredAt);

    List<Alert> pending(Instant now);

    void delivered(String key);

    void retry(String key, Instant retryAt);

    boolean hasPending();

    void release(Lease lease);
}
