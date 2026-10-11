package com.gole.api.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    @DisplayName("거래 진행 알림만 끌 수 없는 분류에 속한다")
    void onlyTradeProgressIsMandatory() {
        assertThat(Arrays.stream(NotificationType.values())
                        .filter(type -> type.category().mandatory()))
                .containsExactlyInAnyOrder(
                        NotificationType.ORDER_PLACED, NotificationType.ORDER_PAID, NotificationType.GENERAL);
    }

    @Test
    @DisplayName("제안·입찰 알림은 하나의 분류로 함께 끈다")
    void offerAndBidShareOneCategory() {
        assertThat(NotificationType.OFFER_RECEIVED.category()).isEqualTo(NotificationCategory.OFFER);
        assertThat(NotificationType.BID_FILLED.category()).isEqualTo(NotificationCategory.OFFER);
        assertThat(NotificationType.LISTING_PRICE_DROPPED.category()).isEqualTo(NotificationCategory.WATCH);
    }
}
