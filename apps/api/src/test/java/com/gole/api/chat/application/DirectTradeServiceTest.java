package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.chat.application.port.out.DirectTradeNotifierPort;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import com.mongodb.MongoException;
import com.mongodb.client.result.UpdateResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DirectTradeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final ChatRoomMongoRepository rooms = mock(ChatRoomMongoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MarkListingSoldUseCase markSold = mock(MarkListingSoldUseCase.class);
    private final GetLaunchConfigUseCase launch = mock(GetLaunchConfigUseCase.class);
    private final DirectTradeNotifierPort notifier = mock(DirectTradeNotifierPort.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final DirectTradeService service;

    DirectTradeServiceTest() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.PREPARING, Map.of(), NOW, "admin"));
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        service = new DirectTradeService(
                rooms, mongo, markSold, launch, notifier, transactions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirm_waitsUntilBothParticipantsConfirm() {
        ChatRoomDocument room = room();
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        ChatRoomDocument result = service.confirm("room-1", "buyer-1");

        assertThat(result).isSameAs(room);
        verify(notifier).confirmationRequested("seller-1", "room-1");
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
    }

    @Test
    void repeatedConfirmationDoesNotSendDuplicateNotification() {
        ChatRoomDocument alreadyConfirmed = room();
        ReflectionTestUtils.setField(alreadyConfirmed, "buyerConfirmedAt", NOW.minusSeconds(30));
        when(rooms.findById("room-1")).thenReturn(Optional.of(alreadyConfirmed));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 0L, null));

        service.confirm("room-1", "buyer-1");

        verifyNoInteractions(notifier);
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
    }

    @Test
    void confirm_marksListingSoldWhenSecondParticipantConfirms() {
        ChatRoomDocument before = room();
        ChatRoomDocument bothConfirmed = room();
        ReflectionTestUtils.setField(bothConfirmed, "buyerConfirmedAt", NOW.minusSeconds(30));
        ReflectionTestUtils.setField(bothConfirmed, "sellerConfirmedAt", NOW);
        ChatRoomDocument completed = room();
        ReflectionTestUtils.setField(completed, "buyerConfirmedAt", NOW.minusSeconds(30));
        ReflectionTestUtils.setField(completed, "sellerConfirmedAt", NOW);
        ReflectionTestUtils.setField(completed, "directTradeCompletedAt", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(before), Optional.of(bothConfirmed));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(mongo.findAndModify(any(), any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(completed);
        when(markSold.markDirectTradeSoldIfActive("listing-1")).thenReturn(true);

        ChatRoomDocument result = service.confirm("room-1", "seller-1");

        assertThat(result.getDirectTradeCompletedAt()).isEqualTo(NOW);
        verify(markSold).markDirectTradeSoldIfActive("listing-1");
        verify(notifier).tradeCompleted("buyer-1", "room-1");
        verify(notifier, never()).confirmationRequested(any(), any());
    }

    @Test
    void duplicateRequestThatWinsCompletionStillNotifiesTheActualFirstConfirmer() {
        ChatRoomDocument firstConfirmed = room();
        ReflectionTestUtils.setField(firstConfirmed, "buyerConfirmedAt", NOW.minusSeconds(30));
        ChatRoomDocument bothConfirmed = room();
        ReflectionTestUtils.setField(bothConfirmed, "buyerConfirmedAt", NOW.minusSeconds(30));
        ReflectionTestUtils.setField(bothConfirmed, "sellerConfirmedAt", NOW);
        ChatRoomDocument completed = room();
        ReflectionTestUtils.setField(completed, "buyerConfirmedAt", NOW.minusSeconds(30));
        ReflectionTestUtils.setField(completed, "sellerConfirmedAt", NOW);
        ReflectionTestUtils.setField(completed, "directTradeCompletedAt", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(firstConfirmed), Optional.of(bothConfirmed));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 0L, null));
        when(mongo.findAndModify(any(), any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(completed);
        when(markSold.markDirectTradeSoldIfActive("listing-1")).thenReturn(true);

        service.confirm("room-1", "buyer-1");

        verify(notifier).tradeCompleted("buyer-1", "room-1");
        verify(notifier, never()).tradeCompleted("seller-1", "room-1");
    }

    @Test
    void confirm_rejectsNonParticipant() {
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));

        assertThatThrownBy(() -> service.confirm("room-1", "stranger")).isInstanceOf(ForbiddenException.class);
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void confirm_rejectsNewDirectCompletionAfterPaymentStageOpens() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.TRADING, Map.of(), NOW, "admin"));

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isInstanceOf(ConflictException.class);

        verify(rooms, never()).findById(any());
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void confirm_retriesWriteConflictCode112AndThenSucceeds() {
        MongoException writeConflict = new MongoException(112, "WriteConflict");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenThrow(writeConflict)
                .thenThrow(writeConflict)
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        ChatRoomDocument result = service.confirm("room-1", "buyer-1");

        assertThat(result.getId()).isEqualTo("room-1");
        verify(mongo, times(3)).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    @Test
    void confirm_retriesWrappedTransientTransactionLabel() {
        MongoException transientFailure = new MongoException(251, "transaction aborted");
        transientFailure.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        RuntimeException translated = new IllegalStateException("translated", transientFailure);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenThrow(translated)
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        service.confirm("room-1", "buyer-1");

        verify(mongo, times(2)).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    @Test
    void confirm_stopsAfterThreeTransientRetries() {
        MongoException writeConflict = new MongoException(112, "WriteConflict");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenThrow(writeConflict);

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isSameAs(writeConflict);

        verify(mongo, times(4)).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    @Test
    void confirm_doesNotRetryNonTransientMongoFailure() {
        MongoException duplicateKey = new MongoException(11000, "duplicate key");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenThrow(duplicateKey);

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isSameAs(duplicateKey);

        verify(mongo).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    @Test
    void cancel_retriesWriteConflictCode112AndThenSucceeds() {
        MongoException writeConflict = new MongoException(112, "WriteConflict");
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenThrow(writeConflict)
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        ChatRoomDocument result = service.cancelConfirmation("room-1", "buyer-1");

        assertThat(result.getId()).isEqualTo("room-1");
        verify(mongo, times(2)).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    @Test
    void confirm_doesNotRerunTheTransactionBodyWhenCommitResultIsUnknown() {
        MongoException unknownResult = new MongoException(91, "commit result was lost");
        unknownResult.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        TransactionSystemException commitFailure =
                new TransactionSystemException("could not confirm commit", unknownResult);
        doThrow(commitFailure).when(transactions).commit(any());
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));
        when(mongo.updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isSameAs(commitFailure);

        verify(mongo).updateFirst(any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class));
    }

    private ChatRoomDocument room() {
        return new ChatRoomDocument("room-1", "listing-1", "buyer-1", "seller-1", NOW.minusSeconds(60));
    }
}
