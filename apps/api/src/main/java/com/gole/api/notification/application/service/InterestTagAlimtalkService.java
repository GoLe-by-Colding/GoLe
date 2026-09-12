package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.EnqueueInterestTagAlimtalkUseCase;
import com.gole.api.notification.application.port.out.InterestTagAlimtalkOutboxPort;
import com.gole.api.notification.domain.model.InterestTagAlimtalkEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 매물 등록 트랜잭션에 결정적 ID의 FANOUT 한 건만 적재한다. */
@Service
public class InterestTagAlimtalkService implements EnqueueInterestTagAlimtalkUseCase {

    private final InterestTagAlimtalkOutboxPort outbox;
    private final InterestTagAlimtalkProperties properties;
    private final Clock clock;

    public InterestTagAlimtalkService(
            InterestTagAlimtalkOutboxPort outbox, InterestTagAlimtalkProperties properties, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void enqueue(String sellerId, String listingId, String title, String tagKey, String tagLabel) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        outbox.enqueue(InterestTagAlimtalkEvent.fanout(listingId, sellerId, tagKey, tagLabel, title, now, now));
    }
}
