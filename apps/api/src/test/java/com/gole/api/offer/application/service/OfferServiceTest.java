package com.gole.api.offer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.common.exception.TooManyRequestsException;
import com.gole.api.offer.application.port.in.MakeOfferUseCase.MakeOfferCommand;
import com.gole.api.offer.application.port.out.OfferChatPort;
import com.gole.api.offer.application.port.out.OfferIdGeneratorPort;
import com.gole.api.offer.application.port.out.OfferListingPort;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OfferServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final String ROOM = "room-1";
    private static final String LISTING = "listing-1";
    private static final String BUYER = "buyer-1";
    private static final String SELLER = "seller-1";

    private MutableClock clock;
    private InMemoryOffers offers;
    private FakeListings listings;
    private FakeChat chat;
    private RecordingNotifier notifier;
    private FakeSellerVerification sellerVerification;
    private OfferProperties properties;
    private OfferService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        offers = new InMemoryOffers();
        listings = new FakeListings();
        chat = new FakeChat();
        notifier = new RecordingNotifier();
        sellerVerification = new FakeSellerVerification();
        properties = new OfferProperties();
        AtomicInteger sequence = new AtomicInteger();
        OfferIdGeneratorPort ids = () -> "offer-" + sequence.incrementAndGet();
        service = new OfferService(offers, listings, chat, notifier, sellerVerification, ids, properties, clock);

        listings.put(LISTING, SELLER, 280_000, true);
        chat.listingRoom(ROOM, LISTING, BUYER, SELLER);
    }

    private PriceOffer makeOffer(long price) {
        return service.make(new MakeOfferCommand(ROOM, BUYER, price));
    }

    // ---- 제안 생성 ----

    @Test
    @DisplayName("구매자 제안은 대기 상태로 저장되고 방 메시지와 판매자 알림을 남긴다")
    void make_createsPendingOffer_postsMessage_andNotifiesSeller() {
        PriceOffer offer = makeOffer(250_000);

        assertThat(offer.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(offer.origin()).isEqualTo(OfferOrigin.CHAT);
        assertThat(offer.roomId()).isEqualTo(ROOM);
        assertThat(offer.buyerId()).isEqualTo(BUYER);
        assertThat(offer.sellerId()).isEqualTo(SELLER);
        assertThat(offer.listingPriceAtOffer()).isEqualTo(280_000);
        assertThat(offer.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(48)));
        assertThat(offers.store).containsKey(offer.id());
        assertThat(chat.posts).containsExactly(new Post(ROOM, BUYER, "[가격 제안] 250,000원을 제안했어요"));
        assertThat(notifier.events).containsExactly("received:" + offer.id());
    }

    @Test
    @DisplayName("매물 방이 아니면 400 OFFER_ROOM_NOT_LISTING")
    void make_rejectsNonListingRoom() {
        chat.socialRoom("room-dm");

        assertThatThrownBy(() -> service.make(new MakeOfferCommand("room-dm", BUYER, 250_000)))
                .extracting("code")
                .isEqualTo(OfferErrors.ROOM_NOT_LISTING);
        assertThat(offers.store).isEmpty();
    }

    @Test
    @DisplayName("채팅 전송 가드(차단·정지·종료)에 걸리면 그 오류를 그대로 낸다")
    void make_propagatesChatSendGuardFailure() {
        chat.sendFailure = new ForbiddenException("CHAT_BLOCKED", "blocked");

        assertThatThrownBy(() -> makeOffer(250_000)).extracting("code").isEqualTo("CHAT_BLOCKED");
        assertThat(offers.store).isEmpty();
    }

    @Test
    @DisplayName("방의 판매자가 제안하면 403 OFFER_BUYER_ONLY")
    void make_rejectsSellerOfRoom() {
        assertThatThrownBy(() -> service.make(new MakeOfferCommand(ROOM, SELLER, 250_000)))
                .extracting("code")
                .isEqualTo(OfferErrors.BUYER_ONLY);
    }

    @Test
    @DisplayName("판매자 신원확인이 안 됐으면 제안을 만들지 않는다")
    void make_requiresVerifiedSeller() {
        sellerVerification.unverified.add(SELLER);

        assertThatThrownBy(() -> makeOffer(250_000)).isInstanceOf(ServiceUnavailableException.class);
        assertThat(offers.store).isEmpty();
        assertThat(chat.posts).isEmpty();
    }

    @Test
    @DisplayName("매물이 판매 중이 아니거나 없거나 방의 판매자 매물이 아니면 409 OFFER_LISTING_UNAVAILABLE")
    void make_rejectsUnavailableListing() {
        listings.put(LISTING, SELLER, 280_000, false);
        assertThatThrownBy(() -> makeOffer(250_000)).extracting("code").isEqualTo(OfferErrors.LISTING_UNAVAILABLE);

        listings.put(LISTING, "someone-else", 280_000, true);
        assertThatThrownBy(() -> makeOffer(250_000)).extracting("code").isEqualTo(OfferErrors.LISTING_UNAVAILABLE);

        listings.byId.clear();
        assertThatThrownBy(() -> makeOffer(250_000)).extracting("code").isEqualTo(OfferErrors.LISTING_UNAVAILABLE);
        assertThat(offers.store).isEmpty();
    }

    @Test
    @DisplayName("가격이 0 이하이거나 매물가 이상이면 400 OFFER_PRICE_INVALID")
    void make_rejectsPriceOutsideRange() {
        for (long price : new long[] {0, -1_000, 280_000, 280_001}) {
            assertThatThrownBy(() -> makeOffer(price)).extracting("code").isEqualTo(OfferErrors.PRICE_INVALID);
        }
        assertThat(offers.store).isEmpty();
    }

    @Test
    @DisplayName("유효한 대기 제안이 있으면 409 OFFER_ALREADY_PENDING")
    void make_rejectsSecondValidPending() {
        makeOffer(250_000);
        clock.advance(Duration.ofHours(47));

        assertThatThrownBy(() -> makeOffer(260_000)).extracting("code").isEqualTo(OfferErrors.ALREADY_PENDING);
        assertThat(offers.store).hasSize(1);
    }

    @Test
    @DisplayName("만료된 대기 제안은 EXPIRED로 원자 전이해 자리를 비우고 새 제안을 받는다")
    void make_expiresStalePendingBeforeInsert() {
        PriceOffer first = makeOffer(250_000);
        clock.advance(Duration.ofHours(48));

        PriceOffer second = makeOffer(260_000);

        assertThat(second.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(offers.store.get(first.id()).status()).isEqualTo(OfferStatus.EXPIRED);
    }

    @Test
    @DisplayName("같은 매물에 24시간 안에 5건을 넘기면 429 OFFER_RATE_LIMITED, 창을 벗어나면 다시 받는다")
    void make_rateLimitsSixthOfferWithinADay() {
        for (int i = 0; i < 5; i++) {
            PriceOffer offer = makeOffer(250_000 + i);
            service.withdraw(offer.id(), BUYER);
            clock.advance(Duration.ofHours(1));
        }

        assertThatThrownBy(() -> makeOffer(255_000)).isInstanceOfSatisfying(TooManyRequestsException.class, limited -> {
            assertThat(limited.getCode()).isEqualTo(OfferErrors.RATE_LIMITED);
            // 첫 제안(T0)이 창을 벗어나는 시각까지: T0 + 24h - (T0 + 5h)
            assertThat(limited.getRetryAfter()).isEqualTo(Duration.ofHours(19));
        });

        clock.set(T0.plus(Duration.ofHours(24)));
        assertThat(makeOffer(255_000).status()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    @DisplayName("한도 설정을 따른다")
    void make_honoursConfiguredRateLimit() {
        properties.setMaxPerListingPerDay(1);
        PriceOffer offer = makeOffer(250_000);
        service.withdraw(offer.id(), BUYER);

        assertThatThrownBy(() -> makeOffer(250_000)).extracting("code").isEqualTo(OfferErrors.RATE_LIMITED);
    }

    @Test
    @DisplayName("방 메시지·알림 실패는 흡수한다 — 제안 상태가 원장이다")
    void make_absorbsChatAndNotificationFailures() {
        chat.failPost = true;
        notifier.fail = true;

        PriceOffer offer = makeOffer(250_000);

        assertThat(offer.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(offers.store).containsKey(offer.id());
    }

    // ---- 수락 ----

    @Test
    @DisplayName("판매자 수락은 만료를 수락 + 72시간으로 다시 잡고 방 메시지와 구매자 알림을 남긴다")
    void accept_bySeller_resetsExpiry_postsMessage_andNotifiesBuyer() {
        PriceOffer offer = makeOffer(250_000);
        clock.advance(Duration.ofHours(10));
        chat.posts.clear();
        notifier.events.clear();

        PriceOffer accepted = service.accept(offer.id(), SELLER);

        Instant acceptedAt = T0.plus(Duration.ofHours(10));
        assertThat(accepted.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(accepted.respondedAt()).isEqualTo(acceptedAt);
        assertThat(accepted.expiresAt()).isEqualTo(acceptedAt.plus(Duration.ofHours(72)));
        assertThat(chat.posts).containsExactly(new Post(ROOM, SELLER, "[가격 제안] 250,000원 제안을 수락했어요"));
        assertThat(notifier.events).containsExactly("accepted:" + offer.id());
    }

    @Test
    @DisplayName("판매자가 아니면 403 OFFER_ACCESS_DENIED, 없으면 404 OFFER_NOT_FOUND")
    void accept_rejectsNonSellerAndMissingOffer() {
        PriceOffer offer = makeOffer(250_000);

        assertThatThrownBy(() -> service.accept(offer.id(), BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);
        assertThatThrownBy(() -> service.accept(offer.id(), "stranger"))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);
        assertThatThrownBy(() -> service.accept("missing", SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_FOUND);
    }

    @Test
    @DisplayName("만료됐거나 이미 응답한 제안은 409 OFFER_NOT_PENDING")
    void accept_requiresEffectivePending() {
        PriceOffer offer = makeOffer(250_000);
        service.accept(offer.id(), SELLER);
        assertThatThrownBy(() -> service.accept(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_PENDING);

        PriceOffer expiring = makeOffer2();
        clock.advance(Duration.ofHours(48));
        assertThatThrownBy(() -> service.accept(expiring.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_PENDING);
    }

    @Test
    @DisplayName("매물이 더 이상 판매 중이 아니면 수락할 수 없다")
    void accept_requiresActiveListing() {
        PriceOffer offer = makeOffer(250_000);
        listings.put(LISTING, SELLER, 280_000, false);

        assertThatThrownBy(() -> service.accept(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.LISTING_UNAVAILABLE);
        assertThat(offers.store.get(offer.id()).status()).isEqualTo(OfferStatus.PENDING);
    }

    @Test
    @DisplayName("읽은 뒤 구매자가 먼저 철회했으면 수락은 지고 409 OFFER_NOT_PENDING")
    void accept_losesRaceToConcurrentWithdraw() {
        PriceOffer offer = makeOffer(250_000);
        offers.beforeTransition =
                () -> offers.store.put(offer.id(), offers.store.get(offer.id()).withdraw(T0));

        assertThatThrownBy(() -> service.accept(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_PENDING);
        assertThat(offers.store.get(offer.id()).status()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("수락 후 방 메시지·알림 실패는 흡수한다")
    void accept_absorbsSideEffectFailures() {
        PriceOffer offer = makeOffer(250_000);
        chat.failPost = true;
        notifier.fail = true;

        assertThat(service.accept(offer.id(), SELLER).status()).isEqualTo(OfferStatus.ACCEPTED);
    }

    // ---- 거절·철회 ----

    @Test
    @DisplayName("대기 제안 거절은 거절 메시지와 OFFER_DECLINED 알림을 남긴다")
    void decline_pending_postsDeclineMessage() {
        PriceOffer offer = makeOffer(250_000);
        chat.posts.clear();
        notifier.events.clear();

        PriceOffer declined = service.decline(offer.id(), SELLER);

        assertThat(declined.status()).isEqualTo(OfferStatus.DECLINED);
        assertThat(chat.posts).containsExactly(new Post(ROOM, SELLER, "[가격 제안] 250,000원 제안을 거절했어요"));
        assertThat(notifier.events).containsExactly("declined:" + offer.id() + ":false");
    }

    @Test
    @DisplayName("수락한 제안 거절은 수락 취소 메시지를 남긴다")
    void decline_accepted_postsCancellationMessage() {
        PriceOffer offer = makeOffer(250_000);
        service.accept(offer.id(), SELLER);
        chat.posts.clear();
        notifier.events.clear();

        assertThat(service.decline(offer.id(), SELLER).status()).isEqualTo(OfferStatus.DECLINED);
        assertThat(chat.posts).containsExactly(new Post(ROOM, SELLER, "[가격 제안] 250,000원 제안 수락을 취소했어요"));
        assertThat(notifier.events).containsExactly("declined:" + offer.id() + ":true");
    }

    @Test
    @DisplayName("거절은 판매자만, 끝난 제안은 409 OFFER_NOT_OPEN")
    void decline_guards() {
        PriceOffer offer = makeOffer(250_000);
        assertThatThrownBy(() -> service.decline(offer.id(), BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);

        service.decline(offer.id(), SELLER);
        assertThatThrownBy(() -> service.decline(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_OPEN);
    }

    @Test
    @DisplayName("구매자 철회는 방 메시지만 남기고 알림은 보내지 않는다")
    void withdraw_byBuyer_postsMessageWithoutNotification() {
        PriceOffer offer = makeOffer(250_000);
        chat.posts.clear();
        notifier.events.clear();

        PriceOffer withdrawn = service.withdraw(offer.id(), BUYER);

        assertThat(withdrawn.status()).isEqualTo(OfferStatus.WITHDRAWN);
        assertThat(withdrawn.respondedAt()).isEqualTo(T0);
        assertThat(chat.posts).containsExactly(new Post(ROOM, BUYER, "[가격 제안] 250,000원 제안을 철회했어요"));
        assertThat(notifier.events).isEmpty();
    }

    @Test
    @DisplayName("수락된 제안도 구매자가 철회할 수 있고, 판매자는 철회할 수 없으며 끝난 제안은 409 OFFER_NOT_OPEN")
    void withdraw_guards() {
        PriceOffer offer = makeOffer(250_000);
        service.accept(offer.id(), SELLER);
        assertThatThrownBy(() -> service.withdraw(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.ACCESS_DENIED);

        assertThat(service.withdraw(offer.id(), BUYER).status()).isEqualTo(OfferStatus.WITHDRAWN);
        assertThatThrownBy(() -> service.withdraw(offer.id(), BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_OPEN);
        assertThatThrownBy(() -> service.withdraw("missing", BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 수락 제안은 철회·거절할 수 없다")
    void respond_rejectsExpiredAcceptance() {
        PriceOffer offer = makeOffer(250_000);
        service.accept(offer.id(), SELLER);
        clock.advance(Duration.ofHours(72));

        assertThatThrownBy(() -> service.withdraw(offer.id(), BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_OPEN);
        assertThatThrownBy(() -> service.decline(offer.id(), SELLER))
                .extracting("code")
                .isEqualTo(OfferErrors.NOT_OPEN);
    }

    // ---- 조회 ----

    @Test
    @DisplayName("방 조회는 매물 방 참여자만, 최신순이며 상태는 유효 상태다")
    void byRoom_returnsNewestFirstWithEffectiveStatus() {
        PriceOffer first = makeOffer(250_000);
        service.withdraw(first.id(), BUYER);
        clock.advance(Duration.ofMinutes(1));
        PriceOffer second = makeOffer(260_000);
        clock.advance(Duration.ofHours(48));

        List<PriceOffer> result = service.byRoom(ROOM, SELLER);

        assertThat(result).extracting(PriceOffer::id).containsExactly(second.id(), first.id());
        assertThat(result).extracting(PriceOffer::status).containsExactly(OfferStatus.EXPIRED, OfferStatus.WITHDRAWN);

        chat.socialRoom("room-dm");
        assertThatThrownBy(() -> service.byRoom("room-dm", BUYER))
                .extracting("code")
                .isEqualTo(OfferErrors.ROOM_NOT_LISTING);
        chat.readFailure = new ForbiddenException("CHAT_NOT_A_MEMBER", "not a member");
        assertThatThrownBy(() -> service.byRoom(ROOM, "stranger"))
                .extracting("code")
                .isEqualTo("CHAT_NOT_A_MEMBER");
    }

    @Test
    @DisplayName("매물 조회는 판매자면 모든 제안, 구매자면 자기 제안만 본다")
    void byListing_sellerSeesAll_buyerSeesOwn() {
        chat.listingRoom("room-2", LISTING, "buyer-2", SELLER);
        PriceOffer mine = makeOffer(250_000);
        clock.advance(Duration.ofMinutes(1));
        PriceOffer theirs = service.make(new MakeOfferCommand("room-2", "buyer-2", 255_000));

        assertThat(service.byListing(LISTING, SELLER))
                .extracting(PriceOffer::id)
                .containsExactly(theirs.id(), mine.id());
        assertThat(service.byListing(LISTING, BUYER)).extracting(PriceOffer::id).containsExactly(mine.id());
        assertThat(service.byListing(LISTING, "stranger")).isEmpty();
    }

    // ---- 주문 연결 ----

    @Test
    @DisplayName("주문에는 이 매물·이 구매자의 유효 수락 제안 가격만 쓸 수 있다")
    void usablePrice_requiresEffectiveAcceptedForSameListingAndBuyer() {
        PriceOffer offer = makeOffer(250_000);
        assertThat(service.usablePrice(offer.id(), LISTING, BUYER, clock.instant()))
                .isEmpty();

        service.accept(offer.id(), SELLER);
        Instant now = clock.instant();
        assertThat(service.usablePrice(offer.id(), LISTING, BUYER, now)).isEqualTo(OptionalLong.of(250_000));
        assertThat(service.usablePrice(offer.id(), "listing-2", BUYER, now)).isEmpty();
        assertThat(service.usablePrice(offer.id(), LISTING, "buyer-2", now)).isEmpty();
        assertThat(service.usablePrice(offer.id(), LISTING, BUYER, now.plus(Duration.ofHours(72))))
                .isEmpty();
        assertThat(service.usablePrice("missing", LISTING, BUYER, now)).isEmpty();
        assertThat(service.usablePrice(null, LISTING, BUYER, now)).isEmpty();
    }

    @Test
    @DisplayName("입찰 체결 제안은 방 없이 바로 수락 상태로 만들고 매물가 이상이어도 된다")
    void createAccepted_createsBidOfferWithoutRoom() {
        PriceOffer offer = service.createAccepted(LISTING, SELLER, BUYER, 300_000, OfferOrigin.BID);

        assertThat(offer.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(offer.origin()).isEqualTo(OfferOrigin.BID);
        assertThat(offer.roomId()).isNull();
        assertThat(offer.expiresAt()).isEqualTo(T0.plus(Duration.ofHours(72)));
        assertThat(chat.posts).isEmpty();
        assertThat(service.usablePrice(offer.id(), LISTING, BUYER, T0)).isEqualTo(OptionalLong.of(300_000));
    }

    @Test
    @DisplayName("입찰 체결 제안은 그 판매자의 판매 중 매물이어야 한다")
    void createAccepted_requiresSellersActiveListing() {
        assertThatThrownBy(() -> service.createAccepted(LISTING, "other-seller", BUYER, 250_000, OfferOrigin.BID))
                .extracting("code")
                .isEqualTo(OfferErrors.LISTING_UNAVAILABLE);
        listings.put(LISTING, SELLER, 280_000, false);
        assertThatThrownBy(() -> service.createAccepted(LISTING, SELLER, BUYER, 250_000, OfferOrigin.BID))
                .extracting("code")
                .isEqualTo(OfferErrors.LISTING_UNAVAILABLE);
        assertThat(offers.store).isEmpty();
    }

    @Test
    @DisplayName("수락 제안을 철회해도 같은 구매자가 다시 대기 제안을 만들 수 있다")
    void make_allowsNewPendingAfterAcceptedWithdrawn() {
        PriceOffer offer = makeOffer(250_000);
        service.accept(offer.id(), SELLER);
        service.withdraw(offer.id(), BUYER);

        assertThat(makeOffer(240_000).status()).isEqualTo(OfferStatus.PENDING);
    }

    private PriceOffer makeOffer2() {
        chat.listingRoom("room-2", LISTING, "buyer-2", SELLER);
        return service.make(new MakeOfferCommand("room-2", "buyer-2", 255_000));
    }

    // ---- fakes ----

    record Post(String roomId, String senderId, String content) {}

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Mongo 어댑터와 같은 규칙: 저장 PENDING 부분 유일, 상태·만료 시각 조건부 전이. */
    static final class InMemoryOffers implements OfferRepositoryPort {

        final Map<String, PriceOffer> store = new LinkedHashMap<>();
        Runnable beforeTransition = () -> {};

        @Override
        public PriceOffer insert(PriceOffer offer) {
            boolean pendingTaken = offer.status() == OfferStatus.PENDING
                    && store.values().stream()
                            .anyMatch(existing -> existing.status() == OfferStatus.PENDING
                                    && existing.listingId().equals(offer.listingId())
                                    && existing.buyerId().equals(offer.buyerId()));
            if (pendingTaken) {
                throw OfferErrors.alreadyPending();
            }
            store.put(offer.id(), offer);
            return offer;
        }

        @Override
        public Optional<PriceOffer> findById(String offerId) {
            return Optional.ofNullable(offerId).map(store::get);
        }

        @Override
        public Optional<PriceOffer> transition(PriceOffer current, PriceOffer next) {
            beforeTransition.run();
            PriceOffer stored = store.get(current.id());
            if (stored == null
                    || stored.status() != current.status()
                    || !stored.expiresAt().equals(current.expiresAt())) {
                return Optional.empty();
            }
            store.put(next.id(), next);
            return Optional.of(next);
        }

        @Override
        public long expireStalePending(String listingId, String buyerId, Instant now) {
            long changed = 0;
            for (PriceOffer offer : List.copyOf(store.values())) {
                if (offer.status() == OfferStatus.PENDING
                        && offer.listingId().equals(listingId)
                        && offer.buyerId().equals(buyerId)
                        && !offer.expiresAt().isAfter(now)) {
                    store.put(offer.id(), offer.asOf(now));
                    changed++;
                }
            }
            return changed;
        }

        @Override
        public boolean existsValidPending(String listingId, String buyerId, Instant now) {
            return store.values().stream()
                    .anyMatch(offer -> offer.status() == OfferStatus.PENDING
                            && offer.listingId().equals(listingId)
                            && offer.buyerId().equals(buyerId)
                            && offer.expiresAt().isAfter(now));
        }

        @Override
        public List<Instant> createdTimesSince(String listingId, String buyerId, Instant since, int limit) {
            return store.values().stream()
                    .filter(offer -> offer.listingId().equals(listingId)
                            && offer.buyerId().equals(buyerId)
                            && offer.createdAt().isAfter(since))
                    .map(PriceOffer::createdAt)
                    .sorted()
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<PriceOffer> findByRoom(String roomId, int limit) {
            return newestFirst(
                    store.values().stream()
                            .filter(offer -> roomId.equals(offer.roomId()))
                            .toList(),
                    limit);
        }

        @Override
        public List<PriceOffer> findByListingForParty(String listingId, String accountId, int limit) {
            return newestFirst(
                    store.values().stream()
                            .filter(offer -> offer.listingId().equals(listingId) && offer.isParty(accountId))
                            .toList(),
                    limit);
        }

        private static List<PriceOffer> newestFirst(List<PriceOffer> offers, int limit) {
            return offers.stream()
                    .sorted(Comparator.comparing(PriceOffer::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    static final class FakeListings implements OfferListingPort {

        final Map<String, OfferListing> byId = new HashMap<>();

        void put(String listingId, String sellerId, long price, boolean active) {
            byId.put(listingId, new OfferListing(listingId, sellerId, price, active));
        }

        @Override
        public Optional<OfferListing> find(String listingId) {
            return Optional.ofNullable(byId.get(listingId));
        }
    }

    static final class FakeChat implements OfferChatPort {

        final Map<String, ListingRoom> listingRooms = new HashMap<>();
        final Set<String> socialRooms = new HashSet<>();
        final List<Post> posts = new ArrayList<>();
        RuntimeException sendFailure;
        RuntimeException readFailure;
        boolean failPost;

        void listingRoom(String roomId, String listingId, String buyerId, String sellerId) {
            listingRooms.put(roomId, new ListingRoom(roomId, listingId, buyerId, sellerId));
        }

        void socialRoom(String roomId) {
            socialRooms.add(roomId);
        }

        @Override
        public Optional<ListingRoom> requireSendableListingRoom(String roomId, String actorId) {
            if (sendFailure != null) {
                throw sendFailure;
            }
            return resolve(roomId, actorId);
        }

        @Override
        public Optional<ListingRoom> requireReadableListingRoom(String roomId, String actorId) {
            if (readFailure != null) {
                throw readFailure;
            }
            return resolve(roomId, actorId);
        }

        private Optional<ListingRoom> resolve(String roomId, String actorId) {
            if (socialRooms.contains(roomId)) {
                return Optional.empty();
            }
            ListingRoom room = listingRooms.get(roomId);
            if (room == null
                    || (!room.buyerId().equals(actorId) && !room.sellerId().equals(actorId))) {
                throw new ForbiddenException("CHAT_NOT_A_MEMBER", "not a member");
            }
            return Optional.of(room);
        }

        @Override
        public void post(String roomId, String actorId, String content) {
            if (failPost) {
                throw new IllegalStateException("redis down");
            }
            posts.add(new Post(roomId, actorId, content));
        }
    }

    static final class RecordingNotifier implements OfferNotifierPort {

        final List<String> events = new ArrayList<>();
        boolean fail;

        @Override
        public void offerReceived(PriceOffer offer) {
            record("received:" + offer.id());
        }

        @Override
        public void offerAccepted(PriceOffer offer) {
            record("accepted:" + offer.id());
        }

        @Override
        public void offerDeclined(PriceOffer offer, boolean acceptanceCanceled) {
            record("declined:" + offer.id() + ":" + acceptanceCanceled);
        }

        private void record(String event) {
            if (fail) {
                throw new IllegalStateException("notification store down");
            }
            events.add(event);
        }
    }

    static final class FakeSellerVerification implements SellerVerificationPort {

        final Set<String> unverified = new HashSet<>();

        @Override
        public void requireVerifiedSeller(String sellerId) {
            if (unverified.contains(sellerId)) {
                throw new ServiceUnavailableException("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "not ready");
            }
        }
    }
}
