package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.ManageSupportConversationPrivacyUseCase;
import com.gole.api.chat.application.port.out.ChatAccountPort;
import com.gole.api.chat.application.port.out.ChatOrderEvidencePort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantPurgePort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeWrite;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.ChatAccount;
import com.gole.api.chat.domain.model.ChatRoomType;
import com.gole.api.chat.domain.model.SupportPurgeReceipt;
import com.gole.api.chat.domain.model.SupportRetentionHold;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 대화의 법정·분쟁 보존 중지와 명시적 연계 파기 유스케이스.
 *
 * <p>스케줄러나 보존 기간 기반 자동 삭제는 두지 않는다. 관리자가 정확한 방 ID를 재입력하고,
 * 보존 필요성을 검토했음을 확인한 단건 요청만 처리한다.
 */
@Service
public class SupportConversationPrivacyService implements ManageSupportConversationPrivacyUseCase {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

    private final ChatAccountPort accounts;
    private final SupportTicketRepositoryPort tickets;
    private final SocialChatRoomRepositoryPort rooms;
    private final ChatReportSnapshotPort reportSnapshots;
    private final ChatOrderEvidencePort orderEvidence;
    private final SupportConversationPrivacyRepositoryPort privacy;
    private final Clock clock;
    private final SupportAssistantPurgePort assistantPurge;
    private final SupportAssistantAnalysisRepositoryPort analyses;

    public SupportConversationPrivacyService(
            ChatAccountPort accounts,
            SupportTicketRepositoryPort tickets,
            SocialChatRoomRepositoryPort rooms,
            ChatReportSnapshotPort reportSnapshots,
            ChatOrderEvidencePort orderEvidence,
            SupportConversationPrivacyRepositoryPort privacy,
            Clock clock,
            SupportAssistantPurgePort assistantPurge,
            SupportAssistantAnalysisRepositoryPort analyses) {
        this.accounts = accounts;
        this.tickets = tickets;
        this.rooms = rooms;
        this.reportSnapshots = reportSnapshots;
        this.orderEvidence = orderEvidence;
        this.privacy = privacy;
        this.clock = clock;
        this.assistantPurge = assistantPurge;
        this.analyses = analyses;
    }

    @Transactional
    @Override
    public PurgeOutcome purge(
            String roomId,
            String actorId,
            String confirmation,
            PurgeReasonCode reasonCode,
            boolean preservationReviewed,
            String idempotencyKey) {
        requireAdmin(actorId);
        requireExactConfirmation(roomId, confirmation);
        if (!preservationReviewed) {
            throw new BadRequestException("SUPPORT_PURGE_PRESERVATION_REVIEW_REQUIRED", "거래·분쟁·법정 보존 필요성을 먼저 검토해야 합니다");
        }
        if (reasonCode == null) {
            throw new BadRequestException("SUPPORT_PURGE_REASON_REQUIRED", "파기 사유 코드를 선택해야 합니다");
        }
        validateIdempotencyKey(idempotencyKey);
        String keyHash = sha256("gole/support-purge/idempotency-key/v1\0" + idempotencyKey);
        String requestFingerprint = requestFingerprint(idempotencyKey, roomId, reasonCode);

        var byKey = privacy.findPurgeReceiptByIdempotencyKeyHash(keyHash);
        if (byKey.isPresent()) {
            SupportPurgeReceipt receipt = byKey.orElseThrow();
            if (!requestFingerprint.equals(receipt.requestFingerprint())) {
                throw new ConflictException("SUPPORT_PURGE_IDEMPOTENCY_CONFLICT", "같은 멱등 키를 다른 파기 요청에 재사용할 수 없습니다");
            }
            return new PurgeOutcome(receipt, true);
        }
        SupportTicket ticket = requireTicket(roomId);
        if (ticket.status() != SupportStatus.RESOLVED || ticket.resolvedAt() == null) {
            throw new ConflictException("SUPPORT_PURGE_REQUIRES_RESOLVED", "완료된 문의만 파기할 수 있습니다");
        }
        var room = rooms.findById(roomId)
                .filter(found -> found.type() == ChatRoomType.SUPPORT)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
        if (!room.id().equals(ticket.roomId())) {
            throw new ConflictException("SUPPORT_PURGE_ROOM_MISMATCH", "문의와 대화방 식별자가 일치하지 않습니다");
        }
        privacy.findRetentionHold(roomId).filter(SupportRetentionHold::active).ifPresent(ignored -> {
            throw new ConflictException("SUPPORT_PURGE_RETENTION_HOLD", "법정·분쟁 보존 중지는 해제 전까지 우선합니다");
        });
        if (reportSnapshots.existsByRoomId(roomId)) {
            throw new ConflictException("SUPPORT_PURGE_REPORT_EVIDENCE", "채팅 신고 증거가 연결된 문의는 파기할 수 없습니다");
        }
        if (hasActiveOrEvidentiaryOrder(ticket.requesterId())) {
            throw new ConflictException("SUPPORT_PURGE_ACTIVE_ORDER", "진행 중 거래 또는 분쟁이 있는 문의자는 대화를 파기할 수 없습니다");
        }

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        assistantPurge.requireAvailable(analyses.hasRemoteCopyPossible(roomId));
        SupportPurgeReceipt receipt = privacy.purge(new PurgeWrite(
                UUID.randomUUID().toString(),
                roomId,
                ticket.version(),
                ticket.resolvedAt(),
                actorId,
                reasonCode.name(),
                keyHash,
                requestFingerprint,
                now));
        // Mongo 변경/영수증은 아직 미커밋이다. 원격 실패는 전체 rollback으로 성공 보고를 막는다.
        // 원격 성공 뒤 Mongo commit 실패는 tombstone으로 복구한다. 재시도는 동일 원격 영수증을 받는다.
        assistantPurge.purge(roomId, ticket.requesterId());
        return new PurgeOutcome(receipt, false);
    }

    @Transactional
    @Override
    public RetentionHoldOutcome placeRetentionHold(
            String roomId, String actorId, String confirmation, RetentionHoldReasonCode reasonCode) {
        requireAdmin(actorId);
        requireExactConfirmation(roomId, confirmation);
        if (reasonCode == null) {
            throw new BadRequestException("SUPPORT_RETENTION_REASON_REQUIRED", "보존 중지 사유 코드를 선택해야 합니다");
        }
        requireSupportConversation(roomId);
        var current = privacy.findRetentionHold(roomId);
        if (current.filter(SupportRetentionHold::active).isPresent()) {
            SupportRetentionHold hold = current.orElseThrow();
            if (!reasonCode.name().equals(hold.reasonCode())) {
                throw new ConflictException("SUPPORT_RETENTION_HOLD_EXISTS", "다른 사유의 보존 중지가 이미 설정되어 있습니다");
            }
            return new RetentionHoldOutcome(hold, false);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        SupportRetentionHold next = new SupportRetentionHold(
                roomId,
                current.map(SupportRetentionHold::holdReference)
                        .orElseGet(() -> UUID.randomUUID().toString()),
                true,
                reasonCode.name(),
                actorId,
                now,
                null,
                null,
                null,
                current.map(SupportRetentionHold::version).orElse(0L));
        return new RetentionHoldOutcome(privacy.saveRetentionHold(next), true);
    }

    @Transactional
    @Override
    public RetentionHoldOutcome releaseRetentionHold(
            String roomId, String actorId, String confirmation, RetentionReleaseReasonCode reasonCode) {
        requireAdmin(actorId);
        requireExactConfirmation(roomId, confirmation);
        if (reasonCode == null) {
            throw new BadRequestException("SUPPORT_RETENTION_RELEASE_REASON_REQUIRED", "보존 중지 해제 사유 코드를 선택해야 합니다");
        }
        requireSupportConversation(roomId);
        SupportRetentionHold current = privacy.findRetentionHold(roomId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_RETENTION_HOLD_NOT_FOUND", "설정된 보존 중지가 없습니다"));
        if (!current.active()) {
            return new RetentionHoldOutcome(current, false);
        }
        SupportRetentionHold released = new SupportRetentionHold(
                current.roomId(),
                current.holdReference(),
                false,
                current.reasonCode(),
                current.placedBy(),
                current.placedAt(),
                actorId,
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS),
                reasonCode.name(),
                current.version());
        return new RetentionHoldOutcome(privacy.saveRetentionHold(released), true);
    }

    private SupportTicket requireSupportConversation(String roomId) {
        SupportTicket ticket = requireTicket(roomId);
        rooms.findById(roomId)
                .filter(room -> room.type() == ChatRoomType.SUPPORT)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
        return ticket;
    }

    private SupportTicket requireTicket(String roomId) {
        return tickets.findByRoomId(roomId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "운영팀 문의를 찾을 수 없습니다"));
    }

    private boolean hasActiveOrEvidentiaryOrder(String accountId) {
        // 종결되지 않은 주문(결제·자금 보유·분쟁·환불 진행)이 있으면 대화가 분쟁 증거일 수 있다.
        return orderEvidence.hasUnsettledOrder(accountId);
    }

    private ChatAccount requireAdmin(String accountId) {
        ChatAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_ADMIN_NOT_FOUND", "관리자 계정을 찾을 수 없습니다"));
        if (!account.isAdmin() || account.isSuspended()) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        return account;
    }

    private static void requireExactConfirmation(String roomId, String confirmation) {
        if (roomId == null || roomId.isBlank() || !roomId.equals(confirmation)) {
            throw new BadRequestException("SUPPORT_PRIVACY_CONFIRMATION_MISMATCH", "확인 값은 경로의 문의 방 ID와 정확히 일치해야 합니다");
        }
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("SUPPORT_PURGE_IDEMPOTENCY_KEY_INVALID", "멱등 키는 무작위 UUID 형식이어야 합니다");
        }
    }

    private static String requestFingerprint(String idempotencyKey, String roomId, PurgeReasonCode reasonCode) {
        String payload = "gole/support-purge/request/v1\0" + roomId + "\n" + reasonCode.name() + "\ntrue";
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(idempotencyKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", impossible);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
