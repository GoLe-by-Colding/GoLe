package com.gole.api.operations.domain;

import java.time.Instant;

/** 영속 감사 기록. 예외 메시지·인증 정보·이메일·사용자 입력 문구는 포함하지 않는다. */
public record OperationRun(
        String id,
        String jobId,
        String actorId,
        String reasonCode,
        String retryOf,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String resultCode) {
    public OperationRun finish(String status, String resultCode, Instant now) {
        return new OperationRun(id, jobId, actorId, reasonCode, retryOf, status, startedAt, now, resultCode);
    }
}
