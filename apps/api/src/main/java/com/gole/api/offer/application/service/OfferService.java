package com.gole.api.offer.application.service;

import com.gole.api.offer.application.port.in.CreateAcceptedOfferUseCase;
import com.gole.api.offer.application.port.in.ListOffersUseCase;
import com.gole.api.offer.application.port.in.MakeOfferUseCase;
import com.gole.api.offer.application.port.in.ResolveAcceptedOfferUseCase;
import com.gole.api.offer.application.port.in.RespondToOfferUseCase;
import com.gole.api.offer.application.port.out.OfferChatPort;
import com.gole.api.offer.application.port.out.OfferChatPort.ListingRoom;
import com.gole.api.offer.application.port.out.OfferIdGeneratorPort;
import com.gole.api.offer.application.port.out.OfferListingPort;
import com.gole.api.offer.application.port.out.OfferListingPort.OfferListing;
import com.gole.api.offer.application.port.out.OfferNotifierPort;
import com.gole.api.offer.application.port.out.OfferRepositoryPort;
import com.gole.api.offer.application.port.out.SellerVerificationPort;
import com.gole.api.offer.domain.exception.OfferErrors;
import com.gole.api.offer.domain.model.OfferOrigin;
import com.gole.api.offer.domain.model.OfferStatus;
import com.gole.api.offer.domain.model.PriceOffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 가격 제안 유스케이스. (price-offer O1~O21)
 *
 * <p>제안 문서가 원장이다. 방 메시지와 알림은 원장 갱신 뒤의 부수 효과라 실패해도 흡수한다(O6). 그래서
 * 이 서비스는 트랜잭션을 열지 않는다 — 원장 갱신은 단일 문서 원자 연산이고, 부수 효과의 실패가 이미
 * 확정된 제안을 되돌리면 안 된다.
 */
@Service
public class OfferService
        implements MakeOfferUseCase,
                RespondToOfferUseCase,
                ListOffersUseCase,
                ResolveAcceptedOfferUseCase,
                CreateAcceptedOfferUseCase {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(24);
    private static final String MESSAGE_PREFIX = "[가격 제안] ";

    private final OfferRepositoryPort offers;
    private final OfferListingPort listings;
    private final OfferChatPort chat;
    private final OfferNotifierPort notifier;
    private final SellerVerificationPort sellerVerification;
    private final OfferIdGeneratorPort ids;
    private final OfferProperties properties;
    private final Clock clock;

    public OfferService(
            OfferRepositoryPort offers,
            OfferListingPort listings,
            OfferChatPort chat,
            OfferNotifierPort notifier,
            SellerVerificationPort sellerVerification,
            OfferIdGeneratorPort ids,
            OfferProperties properties,
            Clock clock) {
        this.offers = offers;
        this.listings = listings;
        this.chat = chat;
        this.notifier = notifier;
        this.sellerVerification = sellerVerification;
        this.ids = ids;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PriceOffer make(MakeOfferCommand command) {
        Instant now = Instant.now(clock);
        // O2: 채팅 메시지 전송과 같은 가드(참여·종료·차단·정지)를 먼저 통과해야 한다.
        ListingRoom room = chat.requireSendableListingRoom(command.roomId(), command.buyerId())
                .orElseThrow(OfferErrors::roomNotListing);
        if (!room.buyerId().equals(command.buyerId())) {
            throw OfferErrors.buyerOnly();
        }
        sellerVerification.requireVerifiedSeller(room.sellerId());
        OfferListing listing = requireActiveListing(room.listingId(), room.sellerId());
        PriceOffer offer = PriceOffer.propose(
                ids.newId(),
                listing.listingId(),
                room.roomId(),
                room.buyerId(),
                room.sellerId(),
                command.price(),
                listing.price(),
                now,
                properties.pendingTtl());

        // 부분 유일 인덱스는 저장 상태 PENDING을 본다. 만료된 대기 제안이 자리를 차지하지 않게 먼저 비운다.
        offers.expireStalePending(offer.listingId(), offer.buyerId(), now);
        if (offers.existsValidPending(offer.listingId(), offer.buyerId(), now)) {
            throw OfferErrors.alreadyPending();
        }
        requireWithinRateLimit(offer.listingId(), offer.buyerId(), now);
        PriceOffer saved = offers.insert(offer);

        postBestEffort(saved, saved.buyerId(), "%s원을 제안했어요".formatted(won(saved.price())));
        notifyBestEffort("received", saved, () -> notifier.offerReceived(saved));
        return saved.asOf(now);
    }

    @Override
    public PriceOffer accept(String offerId, String actorId) {
        Instant now = Instant.now(clock);
        PriceOffer current = requireOffer(offerId);
        current.requireSeller(actorId);
        if (current.effectiveStatus(now) != OfferStatus.PENDING) {
            throw OfferErrors.notPending();
        }
        requireActiveListing(current.listingId(), current.sellerId());
        PriceOffer accepted = offers.transition(current, current.accept(now, properties.acceptedTtl()))
                .orElseThrow(OfferErrors::notPending);

        postBestEffort(accepted, accepted.sellerId(), "%s원 제안을 수락했어요".formatted(won(accepted.price())));
        notifyBestEffort("accepted", accepted, () -> notifier.offerAccepted(accepted));
        return accepted.asOf(now);
    }

    @Override
    public PriceOffer decline(String offerId, String actorId) {
        Instant now = Instant.now(clock);
        PriceOffer current = requireOffer(offerId);
        current.requireSeller(actorId);
        boolean acceptanceCanceled = current.effectiveStatus(now) == OfferStatus.ACCEPTED;
        PriceOffer declined = offers.transition(current, current.decline(now)).orElseThrow(OfferErrors::notOpen);

        String message = acceptanceCanceled
                ? "%s원 제안 수락을 취소했어요".formatted(won(declined.price()))
                : "%s원 제안을 거절했어요".formatted(won(declined.price()));
        postBestEffort(declined, declined.sellerId(), message);
        notifyBestEffort("declined", declined, () -> notifier.offerDeclined(declined, acceptanceCanceled));
        return declined.asOf(now);
    }

    @Override
    public PriceOffer withdraw(String offerId, String actorId) {
        Instant now = Instant.now(clock);
        PriceOffer current = requireOffer(offerId);
        current.requireBuyer(actorId);
        PriceOffer withdrawn = offers.transition(current, current.withdraw(now)).orElseThrow(OfferErrors::notOpen);

        // O9: 철회는 방 메시지만 남기고 판매자 알림은 보내지 않는다.
        postBestEffort(withdrawn, withdrawn.buyerId(), "%s원 제안을 철회했어요".formatted(won(withdrawn.price())));
        return withdrawn.asOf(now);
    }

    @Override
    public List<PriceOffer> byRoom(String roomId, String actorId) {
        chat.requireReadableListingRoom(roomId, actorId).orElseThrow(OfferErrors::roomNotListing);
        Instant now = Instant.now(clock);
        return offers.findByRoom(roomId, MAX_RESULTS).stream()
                .map(offer -> offer.asOf(now))
                .toList();
    }

    @Override
    public List<PriceOffer> byListing(String listingId, String actorId) {
        // 판매자는 자기 매물의 모든 제안이 sellerId로, 구매자는 자기 제안이 buyerId로 걸린다.
        // 매물 조회 없이 당사자 조건 하나로 O14의 두 갈래를 모두 만족한다.
        Instant now = Instant.now(clock);
        return offers.findByListingForParty(listingId, actorId, MAX_RESULTS).stream()
                .map(offer -> offer.asOf(now))
                .toList();
    }

    @Override
    public OptionalLong usablePrice(String offerId, String listingId, String buyerId, Instant now) {
        if (offerId == null || offerId.isBlank()) {
            return OptionalLong.empty();
        }
        return offers.findById(offerId)
                .filter(offer -> offer.isUsableFor(listingId, buyerId, now))
                .map(offer -> OptionalLong.of(offer.price()))
                .orElse(OptionalLong.empty());
    }

    @Override
    public PriceOffer createAccepted(
            String listingId, String sellerId, String buyerId, long price, OfferOrigin origin) {
        Instant now = Instant.now(clock);
        OfferListing listing = requireActiveListing(listingId, sellerId);
        PriceOffer offer = PriceOffer.preAccepted(
                ids.newId(),
                listing.listingId(),
                buyerId,
                sellerId,
                price,
                listing.price(),
                Objects.requireNonNull(origin, "origin"),
                now,
                properties.acceptedTtl());
        return offers.insert(offer).asOf(now);
    }

    private PriceOffer requireOffer(String offerId) {
        return offers.findById(offerId).orElseThrow(OfferErrors::notFound);
    }

    /** 그 판매자의 판매 중 매물이어야 한다. 없거나 주인이 다르거나 예약·판매·삭제됐으면 409. */
    private OfferListing requireActiveListing(String listingId, String sellerId) {
        return listings.find(listingId)
                .filter(OfferListing::active)
                .filter(listing -> listing.sellerId().equals(sellerId))
                .orElseThrow(OfferErrors::listingUnavailable);
    }

    /** O5: 같은 매물에 24시간 안에 허용 건수를 넘기면 가장 오래된 제안이 창을 벗어날 때까지 막는다. */
    private void requireWithinRateLimit(String listingId, String buyerId, Instant now) {
        int maximum = properties.maxPerListingPerDay();
        List<Instant> recent = offers.createdTimesSince(listingId, buyerId, now.minus(RATE_LIMIT_WINDOW), maximum);
        if (recent.size() < maximum) {
            return;
        }
        Duration retryAfter = Duration.between(now, recent.getFirst().plus(RATE_LIMIT_WINDOW));
        throw OfferErrors.rateLimited(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
    }

    private void postBestEffort(PriceOffer offer, String senderId, String body) {
        if (offer.roomId() == null) {
            return; // 입찰에서 온 제안은 방이 없다.
        }
        try {
            chat.post(offer.roomId(), senderId, MESSAGE_PREFIX + body);
        } catch (RuntimeException failure) {
            log.warn(
                    "가격 제안 방 메시지 실패 offerId={} roomId={} errorType={}",
                    offer.id(),
                    offer.roomId(),
                    failure.getClass().getSimpleName());
        }
    }

    private static void notifyBestEffort(String event, PriceOffer offer, Runnable delivery) {
        try {
            delivery.run();
        } catch (RuntimeException failure) {
            log.warn(
                    "가격 제안 알림 실패 event={} offerId={} errorType={}",
                    event,
                    offer.id(),
                    failure.getClass().getSimpleName());
        }
    }

    /** {@code 250000} → {@code 250,000}. */
    static String won(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
