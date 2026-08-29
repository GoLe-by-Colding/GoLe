package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.adapter.out.persistence.ChatRoomDocument;
import com.gole.api.chat.adapter.out.persistence.ChatRoomMongoRepository;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.listing.application.port.in.MarkListingSoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DirectTradeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final ChatRoomMongoRepository rooms = mock(ChatRoomMongoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MarkListingSoldUseCase markSold = mock(MarkListingSoldUseCase.class);
    private final GetLaunchConfigUseCase launch = mock(GetLaunchConfigUseCase.class);
    private final DirectTradeService service;

    DirectTradeServiceTest() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.PREPARING, Map.of(), NOW, "admin"));
        service = new DirectTradeService(rooms, mongo, markSold, launch, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirm_waitsUntilBothParticipantsConfirm() {
        ChatRoomDocument room = room();
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));

        ChatRoomDocument result = service.confirm("room-1", "buyer-1");

        assertThat(result).isSameAs(room);
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
        when(mongo.findAndModify(any(), any(), any(), org.mockito.ArgumentMatchers.eq(ChatRoomDocument.class)))
                .thenReturn(completed);
        when(markSold.markDirectTradeSoldIfActive("listing-1")).thenReturn(true);

        ChatRoomDocument result = service.confirm("room-1", "seller-1");

        assertThat(result.getDirectTradeCompletedAt()).isEqualTo(NOW);
        verify(markSold).markDirectTradeSoldIfActive("listing-1");
    }

    @Test
    void confirm_rejectsNonParticipant() {
        when(rooms.findById("room-1")).thenReturn(Optional.of(room()));

        assertThatThrownBy(() -> service.confirm("room-1", "stranger")).isInstanceOf(ForbiddenException.class);
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
    }

    @Test
    void confirm_rejectsNewDirectCompletionAfterPaymentStageOpens() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.TRADING, Map.of(), NOW, "admin"));

        assertThatThrownBy(() -> service.confirm("room-1", "buyer-1")).isInstanceOf(ConflictException.class);

        verify(rooms, never()).findById(any());
        verify(markSold, never()).markDirectTradeSoldIfActive(any());
    }

    private ChatRoomDocument room() {
        return new ChatRoomDocument("room-1", "listing-1", "buyer-1", "seller-1", NOW.minusSeconds(60));
    }
}
