package com.gole.api.chat.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 신고 시점 대화 스냅샷.
 *
 * <p>관리자가 살아 있는 방을 뒤지는 대신 <b>신고 시점에 고정된 사본</b>을 보게 하려는 것이다.
 * 실시간 접근권을 한 번 만들면 그 권한은 신고와 무관한 대화에도 쓸 수 있게 되지만, 스냅샷은
 * 볼 수 있는 범위가 신고 대상으로 물리적으로 제한된다. 또한 신고 후 대화가 삭제·수정돼도
 * 증거가 남는다.
 */
public interface ChatReportSnapshotPort {

    String capture(Snapshot snapshot);

    Optional<StoredSnapshot> findByReportId(String reportId);

    record SnapshotMessage(String messageId, String senderId, String content, Instant sentAt) {}

    record Snapshot(
            String reportId,
            String roomId,
            String reportedMessageId,
            String reporterId,
            List<SnapshotMessage> messages,
            Instant capturedAt) {}

    record StoredSnapshot(
            String id,
            String reportId,
            String roomId,
            String reportedMessageId,
            String reporterId,
            List<SnapshotMessage> messages,
            Instant capturedAt) {}
}
