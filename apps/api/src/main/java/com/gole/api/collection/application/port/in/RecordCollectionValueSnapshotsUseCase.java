package com.gole.api.collection.application.port.in;

import java.time.Instant;

/** Inbound port: 보유 항목이 있는 모든 사용자의 오늘 스냅샷을 찍는다. (collection-value-history H2) */
public interface RecordCollectionValueSnapshotsUseCase {

    /** 사용자 하나의 실패는 나머지를 막지 않는다. 같은 날 다시 돌려도 결과가 같다(덮어쓰기). */
    RecordSummary recordAll(Instant now);

    /**
     * @param targets   찍으려 한 사용자 수
     * @param succeeded 저장까지 끝난 수
     * @param failed    실패한 수(사용자 단위 + 대상 조회 실패)
     */
    record RecordSummary(int targets, int succeeded, int failed) {}
}
