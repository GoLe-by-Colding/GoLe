package com.gole.api.bid.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 세트 하나의 구매 호가창. (buy-bids D6)
 *
 * <p>상태 다섯 개를 모두, 좋은 상태부터 담는다. 입찰이 없는 상태도 빈 칸으로 내보내야 화면이 "아직 입찰 없음"을
 * 상태별로 그릴 수 있다. 입찰자 식별자는 담지 않는다 — 공개 응답이다.
 *
 * @param setNumber  세트 번호
 * @param conditions 상태별 호가. {@link BidCondition} 선언 순서
 */
public record BidBook(String setNumber, List<ConditionBook> conditions) {

    /** 상태별로 보여 주는 호가 단 수. */
    public static final int LEVELS_PER_CONDITION = 5;

    public BidBook {
        conditions = List.copyOf(conditions);
    }

    /**
     * 유효 {@code ACTIVE} 입찰만 센다. 만료된 저장 {@code ACTIVE}는 여기서 거른다.
     *
     * @param bids 그 세트의 입찰(상태 무관). 저장소가 미리 걸렀어도 다시 거른다.
     */
    public static BidBook of(String setNumber, Collection<Bid> bids, Instant now) {
        Map<BidCondition, TreeMap<Long, Integer>> byCondition = new EnumMap<>(BidCondition.class);
        for (BidCondition condition : BidCondition.values()) {
            byCondition.put(condition, new TreeMap<>((a, b) -> Long.compare(b, a)));
        }
        for (Bid bid : bids) {
            if (!bid.setNumber().equals(setNumber) || !bid.isActiveAt(now)) {
                continue;
            }
            byCondition.get(bid.condition()).merge(bid.price(), 1, Integer::sum);
        }
        List<ConditionBook> books = new ArrayList<>(BidCondition.values().length);
        for (BidCondition condition : BidCondition.values()) {
            TreeMap<Long, Integer> levels = byCondition.get(condition);
            int count = levels.values().stream().mapToInt(Integer::intValue).sum();
            List<Level> top = levels.entrySet().stream()
                    .limit(LEVELS_PER_CONDITION)
                    .map(entry -> new Level(entry.getKey(), entry.getValue()))
                    .toList();
            Long highest = levels.isEmpty() ? null : levels.firstKey();
            books.add(new ConditionBook(condition, highest, count, top));
        }
        return new BidBook(setNumber, books);
    }

    /**
     * @param highestPrice 최고 입찰가 = 판매자가 지금 팔면 받는 값. 입찰이 없으면 null
     * @param bidCount     그 상태의 유효 입찰 수
     * @param levels       가격 내림차순 상위 {@value #LEVELS_PER_CONDITION}단
     */
    public record ConditionBook(BidCondition condition, Long highestPrice, int bidCount, List<Level> levels) {

        public ConditionBook {
            levels = List.copyOf(levels);
        }
    }

    /** 호가 한 단 — 같은 가격의 입찰 수. */
    public record Level(long price, int count) {}
}
