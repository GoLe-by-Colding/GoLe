package com.gole.api.bid.application.port.out;

import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidCondition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 입찰 원장.
 *
 * <p>같은 사용자·세트·상태의 저장 {@code ACTIVE}는 하나뿐이다(부분 유일 인덱스). 만료된 {@code ACTIVE}는 새 입찰
 * 전에 {@link #expireStale}로 자리를 비운다.
 */
public interface BidRepositoryPort {

    /**
     * @throws com.gole.api.bid.domain.exception.DuplicateActiveBidException 같은 자리의 {@code ACTIVE}가 동시에
     *     들어와 유일 인덱스에 걸렸을 때. 서비스가 갱신 경로로 다시 시도한다
     */
    Bid insert(Bid bid);

    Optional<Bid> findById(String bidId);

    /** 이 사용자·세트·상태의 유효 {@code ACTIVE}. */
    Optional<Bid> findActive(String bidderId, String setNumber, BidCondition condition, Instant now);

    /** 만료 시각이 지난 저장 {@code ACTIVE}를 {@code EXPIRED}로 바꾼다. */
    long expireStale(String bidderId, String setNumber, BidCondition condition, Instant now);

    long countActive(String bidderId, Instant now);

    /**
     * {@code current}를 읽은 뒤 아무도 바꾸지 않았을 때만 {@code next}로 바꾼다(저장 상태·만료 시각·가격 조건).
     *
     * @return 바뀐 입찰. 경합에 졌으면 비어 있음
     */
    Optional<Bid> transition(Bid current, Bid next);

    /**
     * 같은 사용자의 재입찰(D3). 아직 유효 {@code ACTIVE}이기만 하면 가격·기간·만료를 덮어쓴다 — 본인이 연달아 고친
     * 값끼리는 마지막 값이 이긴다. {@link #transition}처럼 읽은 시각까지 조건으로 걸면 동시 재입찰끼리 서로를 계속
     * 무효로 만든다. 체결은 여전히 {@code placedAt}을 조건으로 걸므로, 재입찰 직전 값으로 체결되지 않는다.
     *
     * @return 갱신된 입찰. 그 사이 체결·취소·만료됐으면 비어 있음
     */
    Optional<Bid> replaceIfActive(Bid replaced, Instant now);

    /** 내 입찰. 최신순. */
    List<Bid> findByBidder(String bidderId, int limit);

    /** 세트의 유효 {@code ACTIVE} 전부(호가창). */
    List<Bid> findActiveBySet(String setNumber, Instant now);

    /** 판매자 즉시 판매 후보: 그 세트·상태의 유효 {@code ACTIVE} 중 판매자 본인 것 제외, 높은 가격·먼저 건 순. */
    List<Bid> findFillCandidates(
            String setNumber, BidCondition condition, String excludeBidderId, Instant now, int limit);

    /** 매물가 이상으로 건 유효 {@code ACTIVE}의 입찰자. 중복 없이 최대 {@code limit}명. */
    List<String> findBiddersAtOrAbove(String setNumber, BidCondition condition, long price, Instant now, int limit);
}
