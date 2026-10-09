package com.gole.api.collection.domain.model;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 보유 컬렉션의 추정 가치. (요구사항 11.5, collection-value-history H5)
 *
 * <p>값은 보유({@link OwnershipStatus#OWNED}) 항목마다 최신 체결가를 더한 합이고, 시세가 없는 세트는 0으로 친다.
 * {@code pricedCount}가 따로 있는 이유 — 시세가 잡힌 세트가 적으면 합계가 실제보다 크게 낮다. 화면이 "n/m개
 * 기준"을 함께 적어 그 0이 "가치 없음"이 아니라 "시세 없음"임을 드러낸다.
 *
 * @param ownedValue  보유 항목 최신 체결가 합(원)
 * @param ownedCount  보유 항목 수
 * @param pricedCount 그 가운데 시세가 잡혀 값에 들어간 항목 수
 */
public record CollectionValuation(long ownedValue, int ownedCount, int pricedCount) {

    public static final CollectionValuation EMPTY = new CollectionValuation(0L, 0, 0);

    public CollectionValuation {
        if (ownedValue < 0 || ownedCount < 0 || pricedCount < 0 || pricedCount > ownedCount) {
            throw new IllegalArgumentException("invalid collection valuation");
        }
    }

    /**
     * 항목 목록과 세트별 최신 체결가 조회로 가치를 낸다. 보유가 아닌 항목은 건너뛴다.
     *
     * <p>현재 추정가와 자산 추이 스냅샷이 같은 함수를 쓴다 — 둘이 갈리면 그래프 끝점과 카드 숫자가 어긋난다.
     */
    public static CollectionValuation of(
            Collection<CollectionItem> items, Function<String, Optional<Long>> latestPrice) {
        Objects.requireNonNull(latestPrice, "latestPrice");
        long value = 0L;
        int owned = 0;
        int priced = 0;
        for (CollectionItem item : items) {
            if (!item.isOwned()) {
                continue;
            }
            owned++;
            Optional<Long> price = latestPrice.apply(item.setNumber());
            if (price != null && price.isPresent()) {
                value += Math.max(0L, price.orElseThrow());
                priced++;
            }
        }
        return new CollectionValuation(value, owned, priced);
    }
}
