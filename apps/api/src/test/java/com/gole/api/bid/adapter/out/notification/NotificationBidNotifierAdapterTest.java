package com.gole.api.bid.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 입찰 알림 문구·링크·멱등 키. (buy-bids D7, D8) */
class NotificationBidNotifierAdapterTest {

    private final NotifyUseCase notifications = mock(NotifyUseCase.class);
    private final NotificationBidNotifierAdapter adapter = new NotificationBidNotifierAdapter(notifications);

    @Test
    @DisplayName("체결 알림은 입찰당 한 번, 매물로 가는 링크를 단다")
    void bidFilled_usesSpecMessageAndKey() {
        adapter.bidFilled("buyer-1", "bid-1", "listing-1", 250_000, "에펠탑(10307)");

        ArgumentCaptor<NotifyCommand> sent = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications).notify(sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new NotifyCommand(
                        "buyer-1",
                        NotificationType.BID_FILLED,
                        "판매자가 입찰가 250,000원에 에펠탑(10307) 판매를 수락했어요. 72시간 안에 진행해 주세요",
                        "/listings/listing-1",
                        "bid-filled:bid-1"));
    }

    @Test
    @DisplayName("매칭 알림은 매물·가격당 한 번이고 발송 장애를 흡수한다")
    void listingMatched_usesPriceInKeyAndAbsorbsFailure() {
        adapter.listingMatched("buyer-1", "listing-1", "에펠탑 미개봉", 240_000);

        ArgumentCaptor<NotifyCommand> sent = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications).notify(sent.capture());
        assertThat(sent.getValue().message()).isEqualTo("입찰가 이하 매물이 올라왔어요: 에펠탑 미개봉 240,000원");
        assertThat(sent.getValue().deduplicationKey()).isEqualTo("bid-match:listing-1:240000");

        doThrow(new IllegalStateException("push down")).when(notifications).notify(any());
        assertThatCode(() -> adapter.listingMatched("buyer-2", "listing-1", "t", 1))
                .doesNotThrowAnyException();
    }
}
