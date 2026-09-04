package com.gole.api.chat.application.port.out;

import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 문의별 AI 분석 실행권과 완료 결과를 저장하는 포트. 방 ID가 멱등 키다. */
public interface SupportAssistantAnalysisRepositoryPort {

    /** 원문을 복제하지 않고 방 ID만 멱등 작업으로 등록한다. 이미 있으면 아무것도 바꾸지 않는다. */
    boolean enqueue(String roomId, Instant requestedAt);

    /** 재시도 가능하거나 임대가 만료된 작업 하나를 원자적으로 선점한다. */
    Optional<Claim> tryClaim(String roomId, Instant startedAt, Instant leaseUntil, int maxAttempts);

    /** 같은 임대 토큰을 가진 실행자만 완료 상태를 기록할 수 있다. */
    void complete(String roomId, String leaseToken, Analysis analysis, Instant completedAt);

    /** 일시 실패를 다음 실행 시각과 함께 돌려놓는다. */
    void retry(String roomId, String leaseToken, Instant failedAt, Instant nextAttemptAt);

    /** 최대 횟수를 모두 쓴 작업만 최종 실패로 닫는다. */
    void fail(String roomId, String leaseToken, Instant completedAt);

    /** 대기·재시도·임대 만료 작업 중 지금 선점할 수 있는 방 ID를 제한해서 찾는다. */
    List<String> findRecoverableRoomIds(Instant now, int maxAttempts, int limit);

    Optional<StoredAnalysis> findCompletedByRoomId(String roomId);

    List<StoredAnalysis> findCompletedByRoomIds(List<String> roomIds);

    record Claim(String roomId, String leaseToken, int attempt) {}

    record StoredAnalysis(String roomId, Analysis analysis, Instant completedAt) {}
}
