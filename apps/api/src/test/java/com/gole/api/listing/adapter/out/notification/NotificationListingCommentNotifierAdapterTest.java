package com.gole.api.listing.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationListingCommentNotifierAdapterTest {

    private final NotifyUseCase notifications = mock(NotifyUseCase.class);
    private final NotificationListingCommentNotifierAdapter adapter =
            new NotificationListingCommentNotifierAdapter(notifications);

    @Test
    @DisplayName("판매자에게 매물 상세로 가는 문의 알림을 보낸다")
    void notifySellerOfQuestion_sendsCommentNotification() {
        adapter.notifySellerOfQuestion("seller-1", "listing-1", "에펠탑 10307");

        verify(notifications)
                .notify(new NotifyCommand(
                        "seller-1", NotificationType.COMMENT, "매물 '에펠탑 10307'에 문의가 달렸어요.", "/listings/listing-1"));
    }

    @Test
    @DisplayName("긴 제목은 20자에서 줄인다")
    void notifySellerOfQuestion_truncatesLongTitle() {
        adapter.notifySellerOfQuestion("seller-1", "listing-1", "스타워즈 밀레니엄 팔콘 UCS 75192 미개봉 박스 새것");

        verify(notifications)
                .notify(new NotifyCommand(
                        "seller-1",
                        NotificationType.COMMENT,
                        "매물 '스타워즈 밀레니엄 팔콘 UCS 751…'에 문의가 달렸어요.",
                        "/listings/listing-1"));
    }

    @Test
    @DisplayName("알림 실패는 흡수한다")
    void notifySellerOfQuestion_swallowsFailure() {
        when(notifications.notify(any())).thenThrow(new IllegalStateException("down"));

        assertThatCode(() -> adapter.notifySellerOfQuestion("seller-1", "listing-1", "에펠탑"))
                .doesNotThrowAnyException();
    }
}
