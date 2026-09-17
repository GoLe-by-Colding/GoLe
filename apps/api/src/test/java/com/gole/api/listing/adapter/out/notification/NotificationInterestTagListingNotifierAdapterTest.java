package com.gole.api.listing.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.notification.application.port.in.EnqueueInterestTagAlimtalkUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationInterestTagListingNotifierAdapterTest {

    @Test
    void delegatesCatalogKeyAndLabel() {
        EnqueueInterestTagAlimtalkUseCase alimtalk = Mockito.mock(EnqueueInterestTagAlimtalkUseCase.class);
        NotificationInterestTagListingNotifierAdapter adapter =
                new NotificationInterestTagListingNotifierAdapter(alimtalk);

        adapter.notifyInterestTagSubscribers("seller-1", "listing-1", "테크닉 매물", InterestTag.TECHNIC);

        verify(alimtalk).enqueue("seller-1", "listing-1", "테크닉 매물", "technic", "테크닉");
    }

    @Test
    void enqueueFailureDoesNotEscape() {
        EnqueueInterestTagAlimtalkUseCase alimtalk = Mockito.mock(EnqueueInterestTagAlimtalkUseCase.class);
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(alimtalk)
                .enqueue("seller-1", "listing-1", "테크닉 매물", "technic", "테크닉");
        NotificationInterestTagListingNotifierAdapter adapter =
                new NotificationInterestTagListingNotifierAdapter(alimtalk);

        assertThatCode(() ->
                        adapter.notifyInterestTagSubscribers("seller-1", "listing-1", "테크닉 매물", InterestTag.TECHNIC))
                .doesNotThrowAnyException();
    }
}
