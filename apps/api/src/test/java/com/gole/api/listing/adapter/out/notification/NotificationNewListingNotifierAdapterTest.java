package com.gole.api.listing.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gole.api.collection.application.port.in.ListSetHoldersUseCase;
import com.gole.api.collection.domain.model.OwnershipStatus;
import com.gole.api.discovery.application.port.in.ListSellerFollowersUseCase;
import com.gole.api.discovery.application.port.in.ListSetWishersUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class NotificationNewListingNotifierAdapterTest {

    private static final ListSetWishersUseCase NO_WISHERS = setNumber -> List.of();
    private static final ListSetHoldersUseCase NO_HOLDERS = (setNumber, status) -> List.of();

    @Test
    void notifiesSetWatchersOnceEachAndSkipsSeller() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        ListSetHoldersUseCase holders = (setNumber, status) ->
                status == OwnershipStatus.WANTED ? List.of("user-1", "user-2") : List.of("owner");
        NotificationNewListingNotifierAdapter adapter = new NotificationNewListingNotifierAdapter(
                sellerId -> List.of(), setNumber -> List.of("user-1", "seller-1"), holders, notifications);

        adapter.notifySetWatchers("seller-1", "listing-1", "에펠탑 10307", "10307");

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(2)).notify(command.capture());
        assertThat(command.getAllValues())
                .extracting(
                        NotifyCommand::recipientId,
                        NotifyCommand::type,
                        NotifyCommand::link,
                        NotifyCommand::deduplicationKey)
                .containsExactly(
                        tuple(
                                "user-1",
                                NotificationType.WATCHED_SET_LISTING,
                                "/listings/listing-1",
                                "set-listing:listing-1"),
                        tuple(
                                "user-2",
                                NotificationType.WATCHED_SET_LISTING,
                                "/listings/listing-1",
                                "set-listing:listing-1"));
    }

    @Test
    void setWatcherLookupFailureDoesNotEscape() {
        ListSetWishersUseCase wishers = setNumber -> {
            throw new IllegalStateException("discovery unavailable");
        };
        NotificationNewListingNotifierAdapter adapter = new NotificationNewListingNotifierAdapter(
                sellerId -> List.of(), wishers, NO_HOLDERS, Mockito.mock(NotifyUseCase.class));

        assertThatCode(() -> adapter.notifySetWatchers("seller-1", "listing-1", "에펠탑", "10307"))
                .doesNotThrowAnyException();
    }

    @Test
    void notifiesEveryFollowerWithListingDeepLink() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        NotificationNewListingNotifierAdapter adapter = new NotificationNewListingNotifierAdapter(
                sellerId -> List.of("user-1", "user-2"), NO_WISHERS, NO_HOLDERS, notifications);

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
        NotificationNewListingNotifierAdapter adapter = new NotificationNewListingNotifierAdapter(
                sellerId -> List.of("user-1", "user-2"), NO_WISHERS, NO_HOLDERS, notifications);

        assertThatCode(() -> adapter.notifyFollowers("seller-1", "listing-1", "에펠탑"))
                .doesNotThrowAnyException();
        verify(notifications, times(2)).notify(any(NotifyCommand.class));
    }

    @Test
    void followerLookupFailureDoesNotEscape() {
        ListSellerFollowersUseCase followers = sellerId -> {
            throw new IllegalStateException("discovery unavailable");
        };
        NotificationNewListingNotifierAdapter adapter = new NotificationNewListingNotifierAdapter(
                followers, NO_WISHERS, NO_HOLDERS, Mockito.mock(NotifyUseCase.class));

        assertThatCode(() -> adapter.notifyFollowers("seller-1", "listing-1", "에펠탑"))
                .doesNotThrowAnyException();
    }
}
