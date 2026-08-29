package com.gole.api.listing.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gole.api.discovery.application.port.in.ListSellerFollowersUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NotificationNewListingNotifierAdapterTest {

    @Test
    void notifiesEveryFollowerWithListingDeepLink() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        NotificationNewListingNotifierAdapter adapter =
                new NotificationNewListingNotifierAdapter(sellerId -> List.of("user-1", "user-2"), notifications);

        adapter.notifyFollowers("seller-1", "listing-1", "에펠탑 10307");

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(2)).notify(command.capture());
        assertThat(command.getAllValues())
                .extracting(
                        NotifyCommand::recipientId, NotifyCommand::type, NotifyCommand::message, NotifyCommand::link)
                .containsExactly(
                        tuple(
                                "user-1",
                                NotificationType.NEW_LISTING,
                                "팔로우한 셀러의 새 매물: 에펠탑 10307",
                                "/listings/listing-1"),
                        tuple(
                                "user-2",
                                NotificationType.NEW_LISTING,
                                "팔로우한 셀러의 새 매물: 에펠탑 10307",
                                "/listings/listing-1"));
    }

    @Test
    void notificationFailureDoesNotEscapeOrStopOtherRecipients() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        doThrow(new IllegalStateException("temporary failure"))
                .doReturn("notification-2")
                .when(notifications)
                .notify(any(NotifyCommand.class));
        NotificationNewListingNotifierAdapter adapter =
                new NotificationNewListingNotifierAdapter(sellerId -> List.of("user-1", "user-2"), notifications);

        assertThatCode(() -> adapter.notifyFollowers("seller-1", "listing-1", "에펠탑"))
                .doesNotThrowAnyException();
        verify(notifications, times(2)).notify(any(NotifyCommand.class));
    }

    @Test
    void followerLookupFailureDoesNotEscape() {
        ListSellerFollowersUseCase followers = sellerId -> {
            throw new IllegalStateException("discovery unavailable");
        };
        NotificationNewListingNotifierAdapter adapter =
                new NotificationNewListingNotifierAdapter(followers, Mockito.mock(NotifyUseCase.class));

        assertThatCode(() -> adapter.notifyFollowers("seller-1", "listing-1", "에펠탑"))
                .doesNotThrowAnyException();
    }
}
