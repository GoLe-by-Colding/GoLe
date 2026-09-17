package com.gole.api.listing.adapter.out.notification;

import com.gole.api.listing.application.port.out.InterestTagListingNotifierPort;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.notification.application.port.in.EnqueueInterestTagAlimtalkUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 매물 관심 테마 알림 요청을 notification 컨텍스트의 FANOUT 적재 유스케이스로 연결한다. */
@Component
public class NotificationInterestTagListingNotifierAdapter implements InterestTagListingNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationInterestTagListingNotifierAdapter.class);

    private final EnqueueInterestTagAlimtalkUseCase alimtalk;

    public NotificationInterestTagListingNotifierAdapter(EnqueueInterestTagAlimtalkUseCase alimtalk) {
        this.alimtalk = alimtalk;
    }

    @Override
    public void notifyInterestTagSubscribers(String sellerId, String listingId, String title, InterestTag tag) {
        try {
            alimtalk.enqueue(sellerId, listingId, title, tag.key(), tag.label());
        } catch (RuntimeException failure) {
            log.warn(
                    "관심태그 알림톡 FANOUT 적재 실패 sellerId={} listingId={} errorType={}",
                    sellerId,
                    listingId,
                    failure.getClass().getSimpleName());
        }
    }
}
