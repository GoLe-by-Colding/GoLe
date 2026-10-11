package com.gole.api.listing.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gole.api.discovery.application.port.in.ListListingWishersUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 찜한 매물 가격 인하 알림 계약. (listing-edit-and-bump E8, E9) */
class NotificationListingPriceDropNotifierAdapterTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void notifiesEveryWisherExceptSellerWithPriceDropMessageAndLink() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        NotificationListingPriceDropNotifierAdapter adapter = new NotificationListingPriceDropNotifierAdapter(
                listingId -> List.of("user-1", "seller-1", "user-2"), notifications);

        adapter.priceDropped("listing-1", "seller-1", "에펠탑 10307", 280_000, 250_000);

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(2)).notify(command.capture());
        assertThat(command.getAllValues())
                .extracting(
                        NotifyCommand::recipientId,
                        NotifyCommand::type,
                        NotifyCommand::message,
                        NotifyCommand::link,
                        NotifyCommand::deduplicationKey)
                .containsExactly(
                        tuple(
                                "user-1",
                                NotificationType.LISTING_PRICE_DROPPED,
                                "찜한 매물 가격이 내려갔어요: 에펠탑 10307 280,000원 → 250,000원",
                                "/listings/listing-1",
                                "listing-price-drop:listing-1:250000"),
                        tuple(
                                "user-2",
                                NotificationType.LISTING_PRICE_DROPPED,
                                "찜한 매물 가격이 내려갔어요: 에펠탑 10307 280,000원 → 250,000원",
                                "/listings/listing-1",
                                "listing-price-drop:listing-1:250000"));
    }

    @Test
    void deduplicationKeyChangesWhenPriceDropsFurther() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        NotificationListingPriceDropNotifierAdapter adapter =
                new NotificationListingPriceDropNotifierAdapter(listingId -> List.of("user-1"), notifications);

        adapter.priceDropped("listing-1", "seller-1", "에펠탑", 280_000, 250_000);
        adapter.priceDropped("listing-1", "seller-1", "에펠탑", 280_000, 250_000); // 재시도
        adapter.priceDropped("listing-1", "seller-1", "에펠탑", 250_000, 230_000); // 더 내림

        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications, times(3)).notify(command.capture());
        // 같은 인하의 재시도는 같은 키라 notification 컨텍스트가 한 번만 저장한다. 더 내리면 새 키다.
        assertThat(command.getAllValues())
                .extracting(NotifyCommand::deduplicationKey)
                .containsExactly(
                        "listing-price-drop:listing-1:250000",
                        "listing-price-drop:listing-1:250000",
                        "listing-price-drop:listing-1:230000");
    }

    @Test
    void wisherLookupFailureDoesNotEscape() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        ListListingWishersUseCase wishers = listingId -> {
            throw new IllegalStateException("discovery unavailable");
        };
        NotificationListingPriceDropNotifierAdapter adapter =
                new NotificationListingPriceDropNotifierAdapter(wishers, notifications);

        assertThatCode(() -> adapter.priceDropped("listing-1", "seller-1", "에펠탑", 2, 1))
                .doesNotThrowAnyException();
        verify(notifications, never()).notify(any(NotifyCommand.class));
    }

    @Test
    void notificationFailureDoesNotEscapeOrStopOtherWishers() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        doThrow(new IllegalStateException("temporary failure"))
                .doReturn("notification-2")
                .when(notifications)
                .notify(any(NotifyCommand.class));
        NotificationListingPriceDropNotifierAdapter adapter = new NotificationListingPriceDropNotifierAdapter(
                listingId -> List.of("user-1", "user-2"), notifications);

        assertThatCode(() -> adapter.priceDropped("listing-1", "seller-1", "에펠탑", 2, 1))
                .doesNotThrowAnyException();
        verify(notifications, times(2)).notify(any(NotifyCommand.class));
    }

    @Test
    void insideTransactionDeliversOnlyAfterCommit() {
        NotifyUseCase notifications = Mockito.mock(NotifyUseCase.class);
        NotificationListingPriceDropNotifierAdapter adapter =
                new NotificationListingPriceDropNotifierAdapter(listingId -> List.of("user-1"), notifications);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        adapter.priceDropped("listing-1", "seller-1", "에펠탑", 2, 1);

        // 수정이 되돌려질 수 있는 동안에는 보내지 않는다.
        verify(notifications, never()).notify(any(NotifyCommand.class));
        List<TransactionSynchronization> pending = TransactionSynchronizationManager.getSynchronizations();
        assertThat(pending).hasSize(1);
        pending.forEach(TransactionSynchronization::afterCommit);
        verify(notifications, times(1)).notify(any(NotifyCommand.class));
    }
}
