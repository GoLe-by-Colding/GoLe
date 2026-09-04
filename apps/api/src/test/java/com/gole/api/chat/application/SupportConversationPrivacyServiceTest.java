package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.application.SupportConversationPrivacyService.PurgeReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionHoldReasonCode;
import com.gole.api.chat.application.SupportConversationPrivacyService.RetentionReleaseReasonCode;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeCounts;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeReceipt;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeWrite;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.RetentionHold;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportConversationPrivacyServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final String ROOM_ID = "support-room-1";
    private static final String KEY = "550e8400-e29b-41d4-a716-446655440001";

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final SocialChatRoomRepositoryPort rooms = mock(SocialChatRoomRepositoryPort.class);
    private final ChatReportSnapshotPort snapshots = mock(ChatReportSnapshotPort.class);
    private final OrderRepositoryPort orders = mock(OrderRepositoryPort.class);
    private final SupportConversationPrivacyRepositoryPort privacy =
            mock(SupportConversationPrivacyRepositoryPort.class);
    private final SupportConversationPrivacyService service = new SupportConversationPrivacyService(
            accounts, tickets, rooms, snapshots, orders, privacy, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(accounts.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(tickets.findByRoomId(ROOM_ID)).thenReturn(Optional.of(resolvedTicket()));
        when(rooms.findById(ROOM_ID)).thenReturn(Optional.of(SocialChatRoom.support(ROOM_ID, "user-1", "문의", NOW)));
        when(orders.findByBuyerId("user-1")).thenReturn(List.of());
        when(orders.findBySellerId("user-1")).thenReturn(List.of());
        when(privacy.findPurgeReceiptByIdempotencyKeyHash(any())).thenReturn(Optional.empty());
    }

    @Test
    void resolvedConversationIsPurgedOnceAndSameIdempotencyRequestReplaysReceipt() {
        AtomicReference<PurgeReceipt> stored = new AtomicReference<>();
        when(privacy.purge(any())).thenAnswer(invocation -> {
            PurgeWrite write = invocation.getArgument(0);
            PurgeReceipt receipt = receipt(write);
            stored.set(receipt);
            return receipt;
        });

        var first =
                service.purge(ROOM_ID, "admin-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY);
        when(privacy.findPurgeReceiptByIdempotencyKeyHash(any())).thenReturn(Optional.of(stored.get()));
        var replay =
                service.purge(ROOM_ID, "admin-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.receipt()).isEqualTo(first.receipt());
        verify(privacy).purge(any());
    }

    @Test
    void sameIdempotencyKeyCannotBeReusedWithDifferentReason() {
        PurgeReceipt previous = new PurgeReceipt(
                "receipt-1",
                "admin-1",
                PurgeReasonCode.RETENTION_PERIOD_EXPIRED.name(),
                "key-hash",
                "different-request-fingerprint",
                NOW.minusSeconds(10),
                NOW,
                new PurgeCounts(1, 1, 1, 1, 0, 0, 0, 0));
        when(privacy.findPurgeReceiptByIdempotencyKeyHash(any())).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "admin-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("멱등 키");

        verify(privacy, never()).purge(any());
    }

    @Test
    void sameIdempotencyKeyCannotBeReusedForAnotherConversation() {
        PurgeReceipt previous = new PurgeReceipt(
                "receipt-1",
                "admin-1",
                PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED.name(),
                "key-hash",
                "different-request-fingerprint",
                NOW.minusSeconds(10),
                NOW,
                new PurgeCounts(1, 1, 1, 1, 0, 0, 0, 0));
        when(privacy.findPurgeReceiptByIdempotencyKeyHash(any())).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.purge(
                        "another-room",
                        "admin-1",
                        "another-room",
                        PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED,
                        true,
                        KEY))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("멱등 키");

        verify(tickets, never()).findByRoomId(any());
        verify(privacy, never()).purge(any());
    }

    @Test
    void nonAdminOrSuspendedAdminIsRejectedBeforeConversationLookup() {
        when(accounts.findById("user-1"))
                .thenReturn(Optional.of(Account.provisioned(
                        "user-1", new Email("user@gole.test"), new PasswordHash("hash"), Role.USER)));
        Account suspendedAdmin = admin();
        suspendedAdmin.suspend("보안 검토");
        when(accounts.findById("suspended-admin")).thenReturn(Optional.of(suspendedAdmin));

        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "user-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "suspended-admin", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY))
                .isInstanceOf(ForbiddenException.class);

        verify(tickets, never()).findByRoomId(any());
        verify(privacy, never()).purge(any());
    }

    @Test
    void explicitConfirmationAndPreservationReviewAreRequiredBeforeReadingConversation() {
        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "admin-1", "wrong-room", PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "admin-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, false, KEY))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("보존");

        verify(tickets, never()).findByRoomId(any());
        verify(privacy, never()).purge(any());
    }

    @Test
    void unresolvedHoldReportEvidenceAndActiveOrderEachBlockPurge() {
        SupportTicket unresolved = SupportTicket.opened(ROOM_ID, "user-1", NOW);
        when(tickets.findByRoomId(ROOM_ID)).thenReturn(Optional.of(unresolved));
        assertBlocked("완료");

        when(tickets.findByRoomId(ROOM_ID)).thenReturn(Optional.of(resolvedTicket()));
        when(privacy.findRetentionHold(ROOM_ID))
                .thenReturn(Optional.of(new RetentionHold(
                        ROOM_ID,
                        "hold-1",
                        true,
                        RetentionHoldReasonCode.LEGAL_OBLIGATION.name(),
                        "admin-1",
                        NOW,
                        null,
                        null,
                        null,
                        0)));
        assertBlocked("보존");

        when(privacy.findRetentionHold(ROOM_ID)).thenReturn(Optional.empty());
        when(snapshots.existsByRoomId(ROOM_ID)).thenReturn(true);
        assertBlocked("신고 증거");

        when(snapshots.existsByRoomId(ROOM_ID)).thenReturn(false);
        Order disputed = mock(Order.class);
        when(disputed.getStatus()).thenReturn(OrderStatus.DISPUTED);
        when(orders.findByBuyerId("user-1")).thenReturn(List.of(disputed));
        assertBlocked("거래 또는 분쟁");

        verify(privacy, never()).purge(any());
    }

    @Test
    void retentionHoldIsExplicitAndReleaseIsIdempotent() {
        when(privacy.saveRetentionHold(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var placed = service.placeRetentionHold(ROOM_ID, "admin-1", ROOM_ID, RetentionHoldReasonCode.ACTIVE_DISPUTE);
        RetentionHold active = placed.hold();
        when(privacy.findRetentionHold(ROOM_ID)).thenReturn(Optional.of(active));
        var released =
                service.releaseRetentionHold(ROOM_ID, "admin-1", ROOM_ID, RetentionReleaseReasonCode.DISPUTE_CLOSED);
        when(privacy.findRetentionHold(ROOM_ID)).thenReturn(Optional.of(released.hold()));
        var replay =
                service.releaseRetentionHold(ROOM_ID, "admin-1", ROOM_ID, RetentionReleaseReasonCode.DISPUTE_CLOSED);

        assertThat(placed.changed()).isTrue();
        assertThat(released.changed()).isTrue();
        assertThat(released.hold().active()).isFalse();
        assertThat(replay.changed()).isFalse();
    }

    private void assertBlocked(String message) {
        assertThatThrownBy(() -> service.purge(
                        ROOM_ID, "admin-1", ROOM_ID, PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED, true, KEY))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(message);
    }

    private static Account admin() {
        return Account.provisioned("admin-1", new Email("admin@gole.test"), new PasswordHash("hash"), Role.ADMIN);
    }

    private static SupportTicket resolvedTicket() {
        return SupportTicket.opened(ROOM_ID, "user-1", NOW.minusSeconds(60))
                .assignTo("admin-1", NOW.minusSeconds(30))
                .resolve(NOW.minusSeconds(10));
    }

    private static PurgeReceipt receipt(PurgeWrite write) {
        return new PurgeReceipt(
                write.receiptId(),
                write.actorId(),
                write.reasonCode(),
                write.idempotencyKeyHash(),
                write.requestFingerprint(),
                write.resolvedAt(),
                write.purgedAt(),
                new PurgeCounts(2, 1, 1, 1, 1, 2, 0, 0));
    }
}
