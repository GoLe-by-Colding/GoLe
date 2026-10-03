package com.gole.api.catalog.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.discovery.application.port.in.ListSetWishersUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NotificationSetRetirementNotifierAdapterTest {

    /** owner 는 보유, wanted 는 희망, both 는 보유이면서 위시리스트에도 담았다. */
    private static final ListSetHoldersUseCase HOLDERS = (setNumber, status) -> switch (status) {
        case OWNED -> List.of("owner", "both");
        case WANTED -> List.of("wanted");
        case SOLD -> List.of("sold");
    };

    private static final ListSetWishersUseCase WISHERS = setNumber -> List.of("wisher", "both", "wanted");

    @Test
    void retiringSoon_notifiesWishersAndWantedOnce_notOwners() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        new NotificationSetRetirementNotifierAdapter(WISHERS, HOLDERS, notifications)
                .retirementChanged("10307", "에펠탑", RetirementStatus.RETIRING_SOON);

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(3)).notify(command.capture());
        assertThat(command.getAllValues())
                .extracting(
                        NotifyCommand::recipientId,
                        NotifyCommand::type,
                        NotifyCommand::link,
                        NotifyCommand::deduplicationKey)
                .containsExactly(
                        tuple(
                                "wisher",
                                NotificationType.SET_RETIREMENT,
                                "/sets/10307",
                                "set-retirement:10307:RETIRING_SOON"),
                        tuple(
                                "both",
                                NotificationType.SET_RETIREMENT,
                                "/sets/10307",
                                "set-retirement:10307:RETIRING_SOON"),
                        tuple(
                                "wanted",
                                NotificationType.SET_RETIREMENT,
                                "/sets/10307",
                                "set-retirement:10307:RETIRING_SOON"));
        assertThat(command.getAllValues())
                .allSatisfy(c -> assertThat(c.message()).contains("곧 단종"));
    }

    @Test
    void retired_sendsOwnerMessageToOwners_andWatcherMessageToTheRest() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        new NotificationSetRetirementNotifierAdapter(WISHERS, HOLDERS, notifications)
                .retirementChanged("10307", "에펠탑", RetirementStatus.RETIRED);

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(4)).notify(command.capture());
        assertThat(command.getAllValues())
                .extracting(NotifyCommand::recipientId, NotifyCommand::message)
                .containsExactly(
                        tuple("owner", "보유한 「에펠탑」 단종됐어요. 지금 시세를 확인해 보세요"),
                        tuple("both", "보유한 「에펠탑」 단종됐어요. 지금 시세를 확인해 보세요"),
                        tuple("wisher", "관심 세트 「에펠탑」 단종됐어요. 남은 매물을 확인해 보세요"),
                        tuple("wanted", "관심 세트 「에펠탑」 단종됐어요. 남은 매물을 확인해 보세요"));
    }

    @Test
    void active_isNeverAnnounced() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        new NotificationSetRetirementNotifierAdapter(WISHERS, HOLDERS, notifications)
                .retirementChanged("10307", "에펠탑", RetirementStatus.ACTIVE);

        verify(notifications, never()).notify(any(NotifyCommand.class));
    }

    @Test
    void failuresAreAbsorbed() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        doThrow(new IllegalStateException("push down")).when(notifications).notify(any(NotifyCommand.class));
        ListSetWishersUseCase broken = setNumber -> {
            throw new IllegalStateException("discovery unavailable");
        };

        assertThatCode(() -> new NotificationSetRetirementNotifierAdapter(WISHERS, HOLDERS, notifications)
                        .retirementChanged("10307", "에펠탑", RetirementStatus.RETIRED))
                .doesNotThrowAnyException();
        verify(notifications, times(4)).notify(any(NotifyCommand.class));
        assertThatCode(() -> new NotificationSetRetirementNotifierAdapter(broken, HOLDERS, notifications)
                        .retirementChanged("10307", "에펠탑", RetirementStatus.RETIRED))
                .doesNotThrowAnyException();
    }
}
