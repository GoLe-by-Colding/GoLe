package com.gole.api.bid.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.bid.application.port.in.FillBidUseCase.FillResult;
import com.gole.api.bid.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.gole.api.bid.application.port.out.BidCatalogPort;
import com.gole.api.bid.application.port.out.BidListingPort;
import com.gole.api.bid.application.port.out.BidListingPort.BidListing;
import com.gole.api.bid.application.port.out.BidNotifierPort;
import com.gole.api.bid.application.port.out.BidOfferPort;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidBook.ConditionBook;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.bid.domain.model.BidStatus;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.DomainException;
import com.gole.api.common.exception.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 구매 입찰 유스케이스. (buy-bids D2~D7, B4) */
class BidServiceTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");

    private InMemoryBidRepository bids;
    private MutableClock clock;
    private Map<String, BidListing> listings;
    private RecordingOffers offers;
    private RecordingNotifier notifier;
    private BidService service;

    @BeforeEach
    void setUp() {
        bids = new InMemoryBidRepository();
        clock = new MutableClock(T0);
        listings = new HashMap<>();
        offers = new RecordingOffers();
        notifier = new RecordingNotifier();
        AtomicInteger ids = new AtomicInteger();
        BidCatalogPort catalog = setNumber -> setNumber.equals("10307") || setNumber.equals("75192")
                ? Optional.of(setNumber.equals("10307") ? "에펠탑" : "밀레니엄 팔콘")
                : Optional.empty();
        BidListingPort listingPort = listingId -> {
            BidListing listing = listings.get(listingId);
            if (listing == null) {
                throw new NotFoundException("LISTING_NOT_FOUND", "매물을 찾을 수 없습니다");
            }
            return listing;
        };
        service = new BidService(
                bids, () -> "bid-" + ids.incrementAndGet(), catalog, listingPort, offers, notifier, clock);
    }

    private Bid place(String bidder, String condition, long price) {
        return service.place(new PlaceBidCommand(bidder, "10307", condition, price, 30));
    }

    private static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            return error.getCode();
        }
        throw new AssertionError("예외가 나야 한다");
    }

    // ── 걸기·갱신 (D2, D3) ───────────────────────────────────────────────

    @Test
    @DisplayName("기간을 비우면 30일, 상태·가격·기간·세트를 스펙 코드로 검증한다")
    void place_defaultsAndValidates() {
        Bid bid = service.place(new PlaceBidCommand("buyer-1", " 10307 ", "like_new", 250_000, null));

        assertThat(bid.durationDays()).isEqualTo(30);
        assertThat(bid.setNumber()).isEqualTo("10307");
        assertThat(codeOf(() -> service.place(new PlaceBidCommand("buyer-1", "10307", "mint", 1_000, 30))))
                .isEqualTo(BidErrors.CONDITION_INVALID);
        assertThat(codeOf(() -> service.place(new PlaceBidCommand("buyer-1", "10307", "like_new", 0, 30))))
                .isEqualTo(BidErrors.PRICE_INVALID);
        assertThat(codeOf(() -> service.place(new PlaceBidCommand("buyer-1", "10307", "like_new", 1_000, 90))))
                .isEqualTo(BidErrors.DURATION_INVALID);
        assertThat(codeOf(() -> service.place(new PlaceBidCommand("buyer-1", "99999", "like_new", 1_000, 30))))
                .isEqualTo(BidErrors.SET_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 세트·상태에 다시 걸면 새로 만들지 않고 가격·만료를 갱신한다")
    void place_sameSetAndConditionReplacesInsteadOfCreating() {
        Bid first = place("buyer-1", "new_sealed", 250_000);
        clock.advance(Duration.ofDays(2));

        Bid second = service.place(new PlaceBidCommand("buyer-1", "10307", "new_sealed", 260_000, 7));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.price()).isEqualTo(260_000);
        assertThat(second.expiresAt()).isEqualTo(T0.plus(Duration.ofDays(9)));
        assertThat(bids.store).hasSize(1);
        // 상태가 다르면 별개 입찰이다
        place("buyer-1", "like_new", 230_000);
        assertThat(bids.store).hasSize(2);
    }

    @Test
    @DisplayName("만료된 입찰 자리에는 새 입찰을 만들고 옛 입찰은 만료로 남긴다")
    void place_afterExpiryCreatesNewBid() {
        Bid old = place("buyer-1", "new_sealed", 250_000);
        clock.advance(Duration.ofDays(31));

        Bid fresh = place("buyer-1", "new_sealed", 240_000);

        assertThat(fresh.id()).isNotEqualTo(old.id());
        assertThat(bids.store.get(old.id()).status()).isEqualTo(BidStatus.EXPIRED);
    }

    @Test
    @DisplayName("진행 중 입찰이 30건이면 31번째 생성은 막지만, 있는 입찰 갱신은 된다")
    void place_limitsActiveBidsPerBidder() {
        for (int i = 0; i < 30; i++) {
            bids.insert(Bid.place("seed-" + i, "buyer-1", "set-" + i, BidCondition.USED_GOOD, 1_000, 30, T0));
        }
        BidService limited = new BidService(
                bids, () -> "bid-new", setNumber -> Optional.of("세트"), id -> null, offers, notifier, clock);

        assertThat(codeOf(() -> limited.place(new PlaceBidCommand("buyer-1", "10307", "used_good", 1_000, 30))))
                .isEqualTo(BidErrors.LIMIT_EXCEEDED);
        assertThat(limited.place(new PlaceBidCommand("buyer-1", "set-0", "used_good", 2_000, 30))
                        .price())
                .isEqualTo(2_000);
        // 하나를 취소하면 다시 걸 수 있다
        limited.cancel("seed-1", "buyer-1");
        assertThat(limited.place(new PlaceBidCommand("buyer-1", "10307", "used_good", 1_000, 30))
                        .id())
                .isEqualTo("bid-new");
    }

    @Test
    @DisplayName("동시에 같은 자리를 만들어 유일 인덱스에 걸리면 이미 있는 입찰을 갱신한다")
    void place_concurrentCreateFallsBackToReplace() {
        Bid winner = Bid.place("bid-winner", "buyer-1", "10307", BidCondition.NEW_SEALED, 250_000, 30, T0);
        InMemoryBidRepository racing = new InMemoryBidRepository() {
            private boolean firstLookup = true;

            @Override
            public Optional<Bid> findActive(String bidderId, String setNumber, BidCondition condition, Instant now) {
                if (firstLookup) {
                    firstLookup = false; // 우리가 읽은 직후 다른 요청이 winner를 넣었다
                    return Optional.empty();
                }
                return super.findActive(bidderId, setNumber, condition, now);
            }
        };
        racing.insert(winner);
        BidService racingService = new BidService(
                racing, () -> "bid-loser", setNumber -> Optional.of("세트"), id -> null, offers, notifier, clock);

        Bid result = racingService.place(new PlaceBidCommand("buyer-1", "10307", "new_sealed", 270_000, 30));

        assertThat(result.id()).isEqualTo("bid-winner");
        assertThat(result.price()).isEqualTo(270_000);
        assertThat(racing.store).containsOnlyKeys("bid-winner");
    }

    // ── 취소·조회 (D4~D6) ───────────────────────────────────────────────

    @Test
    @DisplayName("취소는 본인만, 진행 중일 때만, 없는 입찰은 404다")
    void cancel_rules() {
        Bid bid = place("buyer-1", "new_sealed", 250_000);

        assertThat(codeOf(() -> service.cancel(bid.id(), "intruder"))).isEqualTo(BidErrors.ACCESS_DENIED);
        service.cancel(bid.id(), "buyer-1");
        assertThat(bids.store.get(bid.id()).status()).isEqualTo(BidStatus.CANCELED);
        assertThat(codeOf(() -> service.cancel(bid.id(), "buyer-1"))).isEqualTo(BidErrors.NOT_ACTIVE);
        assertThatThrownBy(() -> service.cancel("missing", "buyer-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("취소가 경합에 지면 다시 읽는다 — 그 사이 체결됐으면 BID_NOT_ACTIVE, 가격만 바뀌었으면 바뀐 입찰을 취소한다")
    void cancel_rereadsAfterLostRace() {
        Bid filledMeanwhile = Bid.place("bid-a", "buyer-1", "10307", BidCondition.NEW_SEALED, 250_000, 30, T0);
        Bid repricedMeanwhile = Bid.place("bid-b", "buyer-1", "10307", BidCondition.LIKE_NEW, 230_000, 30, T0);
        InMemoryBidRepository racing = new InMemoryBidRepository() {
            private final Set<String> interfered = new HashSet<>();

            @Override
            public Optional<Bid> transition(Bid current, Bid next) {
                if (interfered.add(current.id())) {
                    // 우리가 읽은 직후 다른 요청이 먼저 바꿨다
                    store.put(
                            current.id(),
                            current.id().equals("bid-a")
                                    ? current.fill("listing-x", T0.plusSeconds(1))
                                    : current.replace(240_000, 30, T0.plusSeconds(1)));
                    return Optional.empty();
                }
                return super.transition(current, next);
            }
        };
        racing.insert(filledMeanwhile);
        racing.insert(repricedMeanwhile);
        BidService racingService = new BidService(
                racing, () -> "unused", setNumber -> Optional.of("세트"), id -> null, offers, notifier, clock);

        assertThat(codeOf(() -> racingService.cancel("bid-a", "buyer-1"))).isEqualTo(BidErrors.NOT_ACTIVE);
        racingService.cancel("bid-b", "buyer-1");
        assertThat(racing.store.get("bid-b").status()).isEqualTo(BidStatus.CANCELED);
        assertThat(racing.store.get("bid-b").price()).isEqualTo(240_000);
    }

    @Test
    @DisplayName("내 입찰은 최신순이고 만료를 반영한 상태로 나온다")
    void mine_reflectsEffectiveStatus() {
        place("buyer-1", "new_sealed", 250_000);
        clock.advance(Duration.ofDays(1));
        service.place(new PlaceBidCommand("buyer-1", "10307", "like_new", 230_000, 7));
        clock.advance(Duration.ofDays(10)); // 7일 입찰만 만료

        List<Bid> mine = service.mine("buyer-1");

        assertThat(mine).extracting(Bid::condition).containsExactly(BidCondition.LIKE_NEW, BidCondition.NEW_SEALED);
        assertThat(mine).extracting(Bid::status).containsExactly(BidStatus.EXPIRED, BidStatus.ACTIVE);
    }

    @Test
    @DisplayName("호가창은 상태별 최고가·건수를 낸다")
    void book_summarizesActiveBids() {
        place("buyer-1", "new_sealed", 250_000);
        place("buyer-2", "new_sealed", 270_000);
        place("buyer-3", "used_good", 150_000);

        List<ConditionBook> conditions = service.book("10307").conditions();

        assertThat(conditions.get(0).highestPrice()).isEqualTo(270_000);
        assertThat(conditions.get(0).bidCount()).isEqualTo(2);
        assertThat(conditions.get(2).highestPrice()).isEqualTo(150_000);
        assertThat(service.book("  ").conditions())
                .allSatisfy(c -> assertThat(c.bidCount()).isZero());
    }

    // ── 판매자 즉시 판매 (D7) ─────────────────────────────────────────────

    private void sellerListing(String listingId, String sellerId, String setNumber, String condition, boolean active) {
        listings.put(listingId, new BidListing(listingId, sellerId, setNumber, condition, 280_000, active));
    }

    @Test
    @DisplayName("최고가, 동가면 먼저 건 입찰을 체결하고 그 입찰자에게 수락 제안·알림을 만든다")
    void fill_picksHighestThenEarliestAndCreatesOffer() {
        place("buyer-early", "new_sealed", 260_000);
        clock.advance(Duration.ofMinutes(1));
        place("buyer-late", "new_sealed", 260_000);
        place("buyer-low", "new_sealed", 200_000);
        sellerListing("listing-1", "seller-1", "10307", "new_sealed", true);

        FillResult result = service.fill("10307", "listing-1", "seller-1");

        assertThat(result.bidPrice()).isEqualTo(260_000);
        assertThat(result.offerId()).isEqualTo("offer-1");
        assertThat(offers.calls).containsExactly("listing-1|seller-1|buyer-early|260000");
        Bid filled = bids.store.get(result.bidId());
        assertThat(filled.bidderId()).isEqualTo("buyer-early");
        assertThat(filled.status()).isEqualTo(BidStatus.FILLED);
        assertThat(filled.offerId()).isEqualTo("offer-1");
        assertThat(notifier.filled).containsExactly("buyer-early|listing-1|260000|에펠탑(10307)");
    }

    @Test
    @DisplayName("판매자 본인 입찰은 받지 않는다 — 자기거래 금지")
    void fill_excludesSellersOwnBid() {
        place("seller-1", "new_sealed", 300_000);
        place("buyer-1", "new_sealed", 250_000);
        sellerListing("listing-1", "seller-1", "10307", "new_sealed", true);

        assertThat(service.fill("10307", "listing-1", "seller-1").bidPrice()).isEqualTo(250_000);
    }

    @Test
    @DisplayName("체결 경합에 지면 다음 후보로 넘어가고, 3번 다 지면 BID_NOT_FOUND다")
    void fill_retriesNextCandidateOnLostRace() {
        place("buyer-1", "new_sealed", 270_000);
        place("buyer-2", "new_sealed", 260_000);
        place("buyer-3", "new_sealed", 250_000);
        place("buyer-4", "new_sealed", 240_000);
        sellerListing("listing-1", "seller-1", "10307", "new_sealed", true);

        bids.loseNextTransitions(1, bid -> bid.status() == BidStatus.ACTIVE);
        assertThat(service.fill("10307", "listing-1", "seller-1").bidPrice()).isEqualTo(260_000);

        bids.loseNextTransitions(3, bid -> bid.status() == BidStatus.ACTIVE);
        assertThat(codeOf(() -> service.fill("10307", "listing-1", "seller-1"))).isEqualTo(BidErrors.NOT_FOUND);
        assertThat(bids.store.values().stream().filter(b -> b.status() == BidStatus.ACTIVE))
                .hasSize(3);
    }

    @Test
    @DisplayName("제안 생성이 실패하면 입찰을 ACTIVE로 되돌리고 오류를 그대로 낸다")
    void fill_reopensBidWhenOfferCreationFails() {
        Bid bid = place("buyer-1", "new_sealed", 260_000);
        sellerListing("listing-1", "seller-1", "10307", "new_sealed", true);
        offers.failure = new ConflictException("OFFER_LISTING_UNAVAILABLE", "판매 중이 아님");

        assertThatThrownBy(() -> service.fill("10307", "listing-1", "seller-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("판매 중이 아님");

        Bid stored = bids.store.get(bid.id());
        assertThat(stored.status()).isEqualTo(BidStatus.ACTIVE);
        assertThat(stored.filledListingId()).isNull();
        assertThat(notifier.filled).isEmpty();
    }

    @Test
    @DisplayName("본인 매물·판매 중·같은 세트가 아니면 체결하지 않는다")
    void fill_validatesListing() {
        place("buyer-1", "new_sealed", 260_000);
        sellerListing("mine", "seller-1", "10307", "new_sealed", true);
        sellerListing("sold", "seller-1", "10307", "new_sealed", false);
        sellerListing("other-set", "seller-1", "75192", "new_sealed", true);
        sellerListing("no-set", "seller-1", null, "new_sealed", true);
        sellerListing("other-condition", "seller-1", "10307", "used_good", true);

        assertThat(codeOf(() -> service.fill("10307", "mine", "intruder"))).isEqualTo(BidErrors.LISTING_ACCESS_DENIED);
        assertThat(codeOf(() -> service.fill("10307", "sold", "seller-1"))).isEqualTo(BidErrors.LISTING_MISMATCH);
        assertThat(codeOf(() -> service.fill("10307", "other-set", "seller-1"))).isEqualTo(BidErrors.LISTING_MISMATCH);
        assertThat(codeOf(() -> service.fill("10307", "no-set", "seller-1"))).isEqualTo(BidErrors.LISTING_MISMATCH);
        // 상태는 매물 상태를 쓴다 — 사용감 있음 매물로는 미개봉 입찰을 받을 수 없다
        assertThat(codeOf(() -> service.fill("10307", "other-condition", "seller-1")))
                .isEqualTo(BidErrors.NOT_FOUND);
        assertThat(offers.calls).isEmpty();
    }

    private static String conditionAt(int index) {
        return BidCondition.values()[index % BidCondition.values().length].key();
    }

    private static final class RecordingOffers implements BidOfferPort {
        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public String createAccepted(String listingId, String sellerId, String bidderId, long price) {
            if (failure != null) {
                throw failure;
            }
            calls.add(listingId + "|" + sellerId + "|" + bidderId + "|" + price);
            return "offer-" + calls.size();
        }
    }

    private static final class RecordingNotifier implements BidNotifierPort {
        private final List<String> filled = new ArrayList<>();
        private final List<String> matched = new ArrayList<>();

        @Override
        public void bidFilled(String bidderId, String bidId, String listingId, long price, String setLabel) {
            filled.add(bidderId + "|" + listingId + "|" + price + "|" + setLabel);
        }

        @Override
        public void listingMatched(String bidderId, String listingId, String title, long price) {
            matched.add(bidderId + "|" + listingId + "|" + price);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
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
}
