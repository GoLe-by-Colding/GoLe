package com.gole.api.bid.application.service;

import com.gole.api.bid.application.port.in.NotifyMatchingBidsUseCase;
import com.gole.api.bid.application.port.out.BidNotifierPort;
import com.gole.api.bid.application.port.out.BidRepositoryPort;
import com.gole.api.bid.domain.model.BidCondition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 입찰가 이하 매물 알림. (buy-bids D8)
 *
 * <p>입찰 저장소와 알림에만 의존한다. listing이 이 유스케이스를 부르고 {@link BidService}는 listing을 부르므로,
 * 둘을 한 빈에 두면 순환이 생긴다.
 */
@Service
public class BidMatchNotificationService implements NotifyMatchingBidsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BidMatchNotificationService.class);

    private final BidRepositoryPort bids;
    private final BidNotifierPort notifier;
    private final Clock clock;

    public BidMatchNotificationService(BidRepositoryPort bids, BidNotifierPort notifier, Clock clock) {
        this.bids = bids;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 수신자 조회 장애도 매물 등록·수정을 실패시키지 않는다(best-effort). */
    @Override
    public void listingAvailable(ListingAvailable listing) {
        if (listing.setNumber() == null || listing.setNumber().isBlank()) {
            return;
        }
        Optional<BidCondition> condition = BidCondition.fromKey(listing.conditionKey());
        if (condition.isEmpty()) {
            return;
        }
        try {
            // 판매자 본인 입찰이 끼어 있을 수 있어 한 명 더 가져와 거른다.
            List<String> bidders = bids.findBiddersAtOrAbove(
                    listing.setNumber(),
                    condition.orElseThrow(),
                    listing.price(),
                    Instant.now(clock),
                    MAX_RECIPIENTS + 1);
            bidders.stream()
                    .filter(bidderId -> !bidderId.equals(listing.sellerId()))
                    .distinct()
                    .limit(MAX_RECIPIENTS)
                    .forEach(bidderId ->
                            notifier.listingMatched(bidderId, listing.listingId(), listing.title(), listing.price()));
        } catch (RuntimeException failure) {
            log.warn("입찰 매칭 알림 수신자 조회 실패 listingId={}: {}", listing.listingId(), failure.getMessage());
        }
    }
}
