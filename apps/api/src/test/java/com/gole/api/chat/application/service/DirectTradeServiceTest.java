package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.DirectTradeGatePort;
import com.gole.api.chat.application.port.out.DirectTradeListingPort;
import com.gole.api.chat.application.port.out.DirectTradeNotifierPort;
import com.gole.api.chat.application.port.out.ListingChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.RetryingTransactionPort;
import com.gole.api.chat.domain.model.ChatRoom;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectTradeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final ListingChatRoomRepositoryPort rooms = mock(ListingChatRoomRepositoryPort.class);
    private final DirectTradeListingPort markSold = mock(DirectTradeListingPort.class);
    private final DirectTradeGatePort gate = mock(DirectTradeGatePort.class);
    private final DirectTradeNotifierPort notifier = mock(DirectTradeNotifierPort.class);
    /** 재시도·트랜잭션 경계는 어댑터 테스트(MongoRetryingTransactionAdapterTest)가 본다. 여기서는 본문을 그대로 실행한다. */
    private final RetryingTransactionPort transactions = new RetryingTransactionPort() {
        @Override
        public <T> T inNewTransaction(String operation, String key, Supplier<T> work) {
            return work.get();
        }
    };

    private final DirectTradeService service;

    DirectTradeServiceTest() {
        when(gate.directTradeOpen()).thenReturn(true);
        service =
                new DirectTradeService(rooms, transactions, markSold, gate, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("한쪽만 확인하면 상대에게 확인 요청을 알리고 판매 완료로 바꾸지 않는다")
    void confirm_waitsUntilBothParticipantsConfirm() {
        ChatRoom buyerConfirmed = room(NOW, null, null);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room(null, null, null)), Optional.of(buyerConfirmed));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, NOW)).thenReturn(true);

        ChatRoom result = service.confirm("room-1", "buyer-1");

        assertThat(result).isSameAs(buyerConfirmed);
        verify(notifier).confirmationRequested("seller-1", "room-1");
        verify(markSold, never()).markSoldIfActive(any());
    }

    @Test
    @DisplayName("이미 확인한 쪽이 다시 확인해도 알림을 또 보내지 않는다")
    void repeatedConfirmationDoesNotSendDuplicateNotification() {
        ChatRoom alreadyConfirmed = room(NOW.minusSeconds(30), null, null);
        when(rooms.findById("room-1")).thenReturn(Optional.of(alreadyConfirmed));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, NOW)).thenReturn(false);

        service.confirm("room-1", "buyer-1");

        verifyNoInteractions(notifier);
        verify(markSold, never()).markSoldIfActive(any());
    }

    @Test
    @DisplayName("두 번째 확인이면 완료하고 매물을 판매 완료로 바꾸며 먼저 확인한 쪽에 알린다")
    void confirm_marksListingSoldWhenSecondParticipantConfirms() {
        ChatRoom bothConfirmed = room(NOW.minusSeconds(30), NOW, null);
        ChatRoom completed = room(NOW.minusSeconds(30), NOW, NOW);
        when(rooms.findById("room-1"))
                .thenReturn(Optional.of(room(NOW.minusSeconds(30), null, null)), Optional.of(bothConfirmed));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.SELLER, NOW)).thenReturn(true);
        when(rooms.completeIfBothConfirmed("room-1", NOW)).thenReturn(Optional.of(completed));
        when(markSold.markSoldIfActive("listing-1")).thenReturn(true);

        ChatRoom result = service.confirm("room-1", "seller-1");

        assertThat(result.directTradeCompletedAt()).isEqualTo(NOW);
        verify(markSold).markSoldIfActive("listing-1");
        verify(notifier).tradeCompleted("buyer-1", "room-1");
        verify(notifier, never()).confirmationRequested(any(), any());
    }

    @Test
    @DisplayName("중복 요청이 완료 경합에서 이겨도 실제로 먼저 확인한 쪽에만 알린다")
    void duplicateRequestThatWinsCompletionStillNotifiesTheActualFirstConfirmer() {
        ChatRoom bothConfirmed = room(NOW.minusSeconds(30), NOW, null);
        ChatRoom completed = room(NOW.minusSeconds(30), NOW, NOW);
        when(rooms.findById("room-1"))
                .thenReturn(Optional.of(room(NOW.minusSeconds(30), null, null)), Optional.of(bothConfirmed));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.BUYER, NOW)).thenReturn(false);
        when(rooms.completeIfBothConfirmed("room-1", NOW)).thenReturn(Optional.of(completed));
        when(markSold.markSoldIfActive("listing-1")).thenReturn(true);

        service.confirm("room-1", "buyer-1");

        verify(notifier).tradeCompleted("buyer-1", "room-1");
        verify(notifier, never()).tradeCompleted("seller-1", "room-1");
    }

    @Test
    @DisplayName("완료 경합에서 지면 갱신된 방을 다시 읽어 돌려준다")
    void confirm_returnsReloadedRoomWhenAnotherRequestCompleted() {
        ChatRoom bothConfirmed = room(NOW.minusSeconds(30), NOW, null);
        ChatRoom completedByOther = room(NOW.minusSeconds(30), NOW, NOW);
        when(rooms.findById("room-1"))
                .thenReturn(
                        Optional.of(room(NOW.minusSeconds(30), null, null)),
                        Optional.of(bothConfirmed),
                        Optional.of(completedByOther));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.SELLER, NOW)).thenReturn(true);
        when(rooms.completeIfBothConfirmed("room-1", NOW)).thenReturn(Optional.empty());

        assertThat(service.confirm("room-1", "seller-1")).isSameAs(completedByOther);
        verify(markSold, never()).markSoldIfActive(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("매물이 이미 팔렸으면 완료를 되돌리도록 충돌로 끝낸다")
    void confirm_rejectsWhenListingIsNoLongerAvailable() {
        when(rooms.findById("room-1"))
                .thenReturn(
                        Optional.of(room(NOW.minusSeconds(30), null, null)),
                        Optional.of(room(NOW.minusSeconds(30), NOW, null)));
        when(rooms.recordConfirmation("room-1", ChatRoom.Party.SELLER, NOW)).thenReturn(true);
        when(rooms.completeIfBothConfirmed("room-1", NOW))
                .thenReturn(Optional.of(room(NOW.minusSeconds(30), NOW, NOW)));
        when(markSold.markSoldIfActive("listing-1")).thenReturn(false);

        assertThatThrownBy(() -> service.confirm("room-1", "seller-1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("DIRECT_TRADE_LISTING_UNAVAILABLE");
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("참여자가 아니면 거부한다")
    void confirm_rejectsNonParticipant() {
        when(rooms.findById("room-1")).thenReturn(Optional.of(room(null, null, null)));

        assertThatThrownBy(() -> service.confirm("room-1", "stranger")).isInstanceOf(ForbiddenException.class);
        verify(rooms, never()).recordConfirmation(anyString(), any(), any());
        verify(markSold, never()).markSoldIfActive(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("결제 거래 단계가 열리면 새 직거래 완료 확인을 받지 않는다")
    void confirm_rejectsNewDirectCompletionAfterPaymentStageOpens() {
        when(gate.directTradeOpen()).thenReturn(false);

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isInstanceOf(ConflictException.class);

        verify(rooms, never()).findById(any());
        verify(markSold, never()).markSoldIfActive(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("매물이 없는 레거시 방에서는 직거래를 완료하지 않는다")
    void confirm_rejectsRoomWithoutListing() {
        ChatRoom legacy = new ChatRoom("room-1", null, "buyer-1", "seller-1", NOW, NOW, null, null, null);
        when(rooms.findById("room-1")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("DIRECT_TRADE_LISTING_ROOM_REQUIRED");
    }

    @Test
    @DisplayName("확인 취소는 그 쪽 확인만 지운다")
    void cancel_clearsActorsConfirmation() {
        ChatRoom cleared = room(null, null, null);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room(NOW, null, null)), Optional.of(cleared));

        assertThat(service.cancelConfirmation("room-1", "buyer-1")).isSameAs(cleared);
        verify(rooms).clearConfirmation("room-1", ChatRoom.Party.BUYER);
    }

    @Test
    @DisplayName("완료된 거래는 확인을 취소할 수 없다")
    void cancel_rejectsCompletedTrade() {
        when(rooms.findById("room-1")).thenReturn(Optional.of(room(NOW, NOW, NOW)));

        assertThatThrownBy(() -> service.cancelConfirmation("room-1", "buyer-1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("DIRECT_TRADE_ALREADY_COMPLETED");
        verify(rooms, never()).clearConfirmation(anyString(), any());
    }

    private static ChatRoom room(Instant buyerConfirmedAt, Instant sellerConfirmedAt, Instant completedAt) {
        Instant created = NOW.minusSeconds(60);
        return new ChatRoom(
                "room-1",
                "listing-1",
                "buyer-1",
                "seller-1",
                created,
                created,
                buyerConfirmedAt,
                sellerConfirmedAt,
                completedAt);
    }
}
