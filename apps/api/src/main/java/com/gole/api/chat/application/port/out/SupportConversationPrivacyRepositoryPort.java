package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.SupportPurgeReceipt;
import com.gole.api.chat.domain.model.SupportRetentionHold;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port: 문의 대화의 보존 중지와 연계 파기 영수증.
 *
 * <p>파기 영수증에는 방 ID·문의 원문·요청자 ID·자유서술 사유를 넣지 않는다. 무작위 영수증 ID와
 * 관리자 ID, 정형화된 사유 코드, 컬렉션별 처리 건수만 남겨 파기 사실은 감사할 수 있게 한다.
 */
public interface SupportConversationPrivacyRepositoryPort {

    Optional<SupportRetentionHold> findRetentionHold(String roomId);

    SupportRetentionHold saveRetentionHold(SupportRetentionHold hold);

    Optional<SupportPurgeReceipt> findPurgeReceiptByIdempotencyKeyHash(String idempotencyKeyHash);

    /** 읽음 처리처럼 원문 컬렉션을 다시 만들 수 있는 지원 기능과 파기를 직렬화한다. */
    void fenceSupportConversation(String roomId, Instant changedAt);

    /** 호출한 응용 서비스의 Mongo 트랜잭션 안에서 모든 연계 레코드를 파기하고 영수증을 삽입한다. */
    SupportPurgeReceipt purge(PurgeWrite command);

    record PurgeWrite(
            String receiptId,
            String roomId,
            long expectedTicketVersion,
            Instant resolvedAt,
            String actorId,
            String reasonCode,
            String idempotencyKeyHash,
            String requestFingerprint,
            Instant purgedAt) {}
}
