package com.gole.api.bid.application.service;

import com.gole.api.bid.application.port.in.CancelBidUseCase;
import com.gole.api.bid.application.port.in.FillBidUseCase;
import com.gole.api.bid.application.port.in.GetBidBookUseCase;
import com.gole.api.bid.application.port.in.ListMyBidsUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase;
import com.gole.api.bid.application.port.out.BidCatalogPort;
import com.gole.api.bid.application.port.out.BidIdGeneratorPort;
import com.gole.api.bid.application.port.out.BidListingPort;
import com.gole.api.bid.application.port.out.BidListingPort.BidListing;
import com.gole.api.bid.application.port.out.BidNotifierPort;
import com.gole.api.bid.application.port.out.BidOfferPort;
import com.gole.api.bid.application.port.out.BidRepositoryPort;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.bid.domain.exception.DuplicateActiveBidException;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidBook;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.common.exception.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 구매 입찰 유스케이스. (buy-bids D2~D7, D9)
 *
 * <p>모든 상태 변경은 "읽은 원본을 조건으로 한 원자 갱신"이다. 경합에 지면 다시 읽어 판단한다 — 트랜잭션 없이도
 * 같은 입찰이 두 번 체결되거나, 취소된 입찰이 체결되지 않는다.
 */
@Service
public class BidService
        implements PlaceBidUseCase, CancelBidUseCase, ListMyBidsUseCase, GetBidBookUseCase, FillBidUseCase {

    private static final Logger log = LoggerFactory.getLogger(BidService.class);

    /** 사용자당 유효 입찰 상한. (D3) */
    static final int MAX_ACTIVE_PER_BIDDER = 30;

    /** 판매자 즉시 판매가 경합에 졌을 때 다음 후보로 넘어가는 최대 횟수. (D7) */
    static final int MAX_FILL_ATTEMPTS = 3;

    /** 갱신·생성 경합 재시도 횟수. 정상 경로는 한 번에 끝난다. */
    private static final int MAX_PLACE_ATTEMPTS = 3;

    private final BidRepositoryPort bids;
    private final BidIdGeneratorPort ids;
    private final BidCatalogPort catalog;
    private final BidListingPort listings;
    private final BidOfferPort offers;
    private final BidNotifierPort notifier;
    private final Clock clock;

    public BidService(
            BidRepositoryPort bids,
            BidIdGeneratorPort ids,
            BidCatalogPort catalog,
            BidListingPort listings,
            BidOfferPort offers,
            BidNotifierPort notifier,
            Clock clock) {
        this.bids = bids;
        this.ids = ids;
        this.catalog = catalog;
        this.listings = listings;
        this.offers = offers;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * 걸기 또는 갱신. (D2, D3)
     *
     * <p>입력 검증 → 세트 확인 → (있으면) 원자 갱신 / (없으면) 상한 확인 후 생성. 같은 자리에 두 요청이 동시에
     * 생성하면 유일 인덱스가 하나만 받고, 진 쪽은 다시 돌아 갱신 경로를 탄다.
     */
    @Override
    public Bid place(PlaceBidCommand command) {
        BidCondition condition = BidCondition.fromKey(command.condition()).orElseThrow(BidErrors::conditionInvalid);
        int durationDays = command.durationDays() == null ? Bid.DEFAULT_DURATION_DAYS : command.durationDays();
        Bid.requireValidPrice(command.price());
        Bid.requireValidDuration(durationDays);
        String setNumber =
                command.setNumber() == null ? "" : command.setNumber().trim();
        if (setNumber.isEmpty() || catalog.setName(setNumber).isEmpty()) {
            throw BidErrors.setNotFound();
        }

        for (int attempt = 0; attempt < MAX_PLACE_ATTEMPTS; attempt++) {
            Instant now = Instant.now(clock);
            Optional<Bid> existing = bids.findActive(command.bidderId(), setNumber, condition, now);
            if (existing.isPresent()) {
                Bid current = existing.orElseThrow();
                Optional<Bid> replaced = bids.replaceIfActive(current.replace(command.price(), durationDays, now), now);
                if (replaced.isPresent()) {
                    return replaced.orElseThrow();
                }
                continue; // 그 사이 체결·취소됐다 — 다시 읽어 생성 여부를 정한다
            }
            if (bids.countActive(command.bidderId(), now) >= MAX_ACTIVE_PER_BIDDER) {
                throw BidErrors.limitExceeded();
            }
            bids.expireStale(command.bidderId(), setNumber, condition, now);
            try {
                return bids.insert(Bid.place(
                        ids.newId(), command.bidderId(), setNumber, condition, command.price(), durationDays, now));
            } catch (DuplicateActiveBidException concurrentPlace) {
                // 같은 자리에 동시에 생성됐다. 다음 회차에 이미 있는 입찰을 갱신한다.
            }
        }
        throw new ConflictException("BID_BUSY", "입찰이 동시에 처리되고 있습니다. 잠시 후 다시 시도해 주세요");
    }

    /**
     * 취소. (D4) 경합에 지면 다시 읽어 판단한다 — 그 사이 체결·취소됐으면 {@code BID_NOT_ACTIVE}, 가격만 바뀌었으면
     * 바뀐 입찰을 취소한다.
     */
    @Override
    public void cancel(String bidId, String actorId) {
        for (int attempt = 0; attempt < MAX_PLACE_ATTEMPTS; attempt++) {
            Bid bid = bids.findById(bidId).orElseThrow(BidErrors::bidMissing);
            Bid canceled = bid.cancel(actorId, Instant.now(clock));
            if (bids.transition(bid, canceled).isPresent()) {
                return;
            }
        }
        throw BidErrors.notActive();
    }

    @Override
    public List<Bid> mine(String bidderId) {
        Instant now = Instant.now(clock);
        return bids.findByBidder(bidderId, MAX_ITEMS).stream()
                .map(bid -> bid.asOf(now))
                .toList();
    }

    @Override
    public BidBook book(String setNumber) {
        String normalized = setNumber == null ? "" : setNumber.trim();
        Instant now = Instant.now(clock);
        return BidBook.of(normalized, normalized.isEmpty() ? List.of() : bids.findActiveBySet(normalized, now), now);
    }

    /**
     * 판매자 즉시 판매. (D7)
     *
     * <p>후보를 높은 가격·먼저 건 순으로 가져와 {@code ACTIVE → FILLED}를 원자 전이한다. 지면 다음 후보로, 최대
     * {@value #MAX_FILL_ATTEMPTS}번. 이긴 입찰로 수락 제안을 만들고, 그게 실패하면 입찰을 {@code ACTIVE}로 되돌린 뒤
     * 오류를 그대로 낸다 — 제안 없이 체결만 남으면 입찰자는 살 길이 없는데 입찰은 사라진다.
     */
    @Override
    public FillResult fill(String setNumber, String listingId, String sellerId) {
        BidListing listing = listings.get(listingId);
        if (!listing.sellerId().equals(sellerId)) {
            throw BidErrors.listingAccessDenied();
        }
        if (!listing.active()) {
            throw BidErrors.listingNotActive();
        }
        if (listing.setNumber() == null || !listing.setNumber().equals(setNumber)) {
            throw BidErrors.listingMismatch();
        }
        BidCondition condition = BidCondition.fromKey(listing.conditionKey()).orElseThrow(BidErrors::listingMismatch);

        Instant now = Instant.now(clock);
        for (Bid candidate : bids.findFillCandidates(setNumber, condition, sellerId, now, MAX_FILL_ATTEMPTS)) {
            Optional<Bid> won = bids.transition(candidate, candidate.fill(listingId, now));
            if (won.isEmpty()) {
                continue; // 다른 판매자가 먼저 채웠거나 입찰자가 취소했다
            }
            Bid filled = won.orElseThrow();
            String offerId = createOfferOrReopen(filled, listing, sellerId);
            recordOffer(filled, offerId);
            notifier.bidFilled(filled.bidderId(), filled.id(), listingId, filled.price(), setLabel(setNumber));
            return new FillResult(filled.id(), filled.price(), offerId, listingId);
        }
        throw BidErrors.noMatchingBid();
    }

    private String createOfferOrReopen(Bid filled, BidListing listing, String sellerId) {
        try {
            return offers.createAccepted(listing.listingId(), sellerId, filled.bidderId(), filled.price());
        } catch (RuntimeException failure) {
            try {
                if (bids.transition(filled, filled.reopen()).isEmpty()) {
                    log.error("입찰 체결 보상 실패 — FILLED로 남음 bidId={} listingId={}", filled.id(), listing.listingId());
                }
            } catch (RuntimeException reopenFailure) {
                failure.addSuppressed(reopenFailure);
            }
            throw failure;
        }
    }

    /** 체결 입찰에 제안 id를 남긴다. 기록 실패는 체결을 되돌리지 않는다 — 제안이 원장이다. */
    private void recordOffer(Bid filled, String offerId) {
        try {
            bids.transition(filled, filled.withOffer(offerId));
        } catch (RuntimeException failure) {
            log.warn("입찰에 제안 id 기록 실패 bidId={} offerId={}: {}", filled.id(), offerId, failure.getMessage());
        }
    }

    private String setLabel(String setNumber) {
        try {
            return catalog.setName(setNumber)
                    .map(name -> name + "(" + setNumber + ")")
                    .orElse(setNumber);
        } catch (RuntimeException lookupFailure) {
            return setNumber;
        }
    }
}
