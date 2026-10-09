package com.gole.api.bid.application.service;

import com.gole.api.bid.application.port.out.BidRepositoryPort;
import com.gole.api.bid.domain.exception.DuplicateActiveBidException;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.bid.domain.model.BidStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 입찰 테스트용 저장소. 실제 어댑터처럼 저장 {@code ACTIVE} 유일성과 원본 조건 전이를 지킨다.
 *
 * <p>{@link #loseNextTransitions}로 다음 전이 몇 번을 "경합에 졌다"로 만들 수 있다 — 동시 체결·취소를 흉내 낸다.
 */
class InMemoryBidRepository implements BidRepositoryPort {

    final Map<String, Bid> store = new LinkedHashMap<>();
    private int transitionsToLose = 0;
    private Predicate<Bid> loseWhen = bid -> true;

    void loseNextTransitions(int count, Predicate<Bid> when) {
        this.transitionsToLose = count;
        this.loseWhen = when;
    }

    @Override
    public Bid insert(Bid bid) {
        boolean occupied = bid.status() == BidStatus.ACTIVE
                && store.values().stream()
                        .anyMatch(other -> other.status() == BidStatus.ACTIVE
                                && other.bidderId().equals(bid.bidderId())
                                && other.setNumber().equals(bid.setNumber())
                                && other.condition() == bid.condition());
        if (occupied) {
            throw new DuplicateActiveBidException();
        }
        store.put(bid.id(), bid);
        return bid;
    }

    @Override
    public Optional<Bid> findById(String bidId) {
        return Optional.ofNullable(store.get(bidId));
    }

    @Override
    public Optional<Bid> findActive(String bidderId, String setNumber, BidCondition condition, Instant now) {
        return store.values().stream()
                .filter(bid -> bid.isActiveAt(now)
                        && bid.bidderId().equals(bidderId)
                        && bid.setNumber().equals(setNumber)
                        && bid.condition() == condition)
                .findFirst();
    }

    @Override
    public long expireStale(String bidderId, String setNumber, BidCondition condition, Instant now) {
        List<Bid> stale = store.values().stream()
                .filter(bid -> bid.status() == BidStatus.ACTIVE
                        && !bid.isActiveAt(now)
                        && bid.bidderId().equals(bidderId)
                        && bid.setNumber().equals(setNumber)
                        && bid.condition() == condition)
                .toList();
        stale.forEach(bid -> store.put(bid.id(), bid.expire()));
        return stale.size();
    }

    @Override
    public long countActive(String bidderId, Instant now) {
        return store.values().stream()
                .filter(bid -> bid.bidderId().equals(bidderId) && bid.isActiveAt(now))
                .count();
    }

    @Override
    public Optional<Bid> transition(Bid current, Bid next) {
        Bid stored = store.get(current.id());
        if (transitionsToLose > 0 && loseWhen.test(current)) {
            transitionsToLose--;
            return Optional.empty();
        }
        if (stored == null
                || stored.status() != current.status()
                || !Objects.equals(stored.placedAt(), current.placedAt())
                || !Objects.equals(stored.expiresAt(), current.expiresAt())) {
            return Optional.empty();
        }
        store.put(next.id(), next);
        return Optional.of(next);
    }

    @Override
    public Optional<Bid> replaceIfActive(Bid replaced, Instant now) {
        Bid stored = store.get(replaced.id());
        if (stored == null || !stored.isActiveAt(now)) {
            return Optional.empty();
        }
        store.put(replaced.id(), replaced);
        return Optional.of(replaced);
    }

    @Override
    public List<Bid> findFilledForListing(String listingId) {
        return store.values().stream()
                .filter(bid -> bid.status() == BidStatus.FILLED && listingId.equals(bid.filledListingId()))
                .toList();
    }

    @Override
    public List<Bid> findByBidder(String bidderId, int limit) {
        return store.values().stream()
                .filter(bid -> bid.bidderId().equals(bidderId))
                .sorted(Comparator.comparing(Bid::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Bid> findActiveBySet(String setNumber, Instant now) {
        return store.values().stream()
                .filter(bid -> bid.setNumber().equals(setNumber) && bid.isActiveAt(now))
                .toList();
    }

    @Override
    public List<Bid> findFillCandidates(
            String setNumber, BidCondition condition, String excludeBidderId, Instant now, int limit) {
        return store.values().stream()
                .filter(bid -> bid.setNumber().equals(setNumber)
                        && bid.condition() == condition
                        && bid.isActiveAt(now)
                        && !bid.bidderId().equals(excludeBidderId))
                .sorted(Comparator.comparingLong(Bid::price).reversed().thenComparing(Bid::placedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> findBiddersAtOrAbove(
            String setNumber, BidCondition condition, long price, Instant now, int limit) {
        List<String> bidders = new ArrayList<>();
        store.values().stream()
                .filter(bid -> bid.setNumber().equals(setNumber)
                        && bid.condition() == condition
                        && bid.isActiveAt(now)
                        && bid.price() >= price)
                .sorted(Comparator.comparingLong(Bid::price).reversed())
                .limit(limit)
                .forEach(bid -> bidders.add(bid.bidderId()));
        return bidders;
    }
}
