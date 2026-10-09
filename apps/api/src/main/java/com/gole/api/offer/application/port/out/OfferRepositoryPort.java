package com.gole.api.offer.application.port.out;

import com.gole.api.offer.domain.model.PriceOffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 제안 원장.
 *
 * <p>같은 매물·구매자의 저장 상태 {@code PENDING}은 하나뿐이다(부분 유일 인덱스). 만료된 {@code PENDING}은
 * 새 제안 전에 {@link #expireStalePending}으로 자리를 비운다.
 */
public interface OfferRepositoryPort {

    /**
     * 새 제안을 넣는다. 같은 매물·구매자의 대기 제안이 동시에 들어와 유일 인덱스에 걸리면
     * {@code OFFER_ALREADY_PENDING}(409)을 던진다.
     */
    PriceOffer insert(PriceOffer offer);

    Optional<PriceOffer> findById(String offerId);

    /**
     * {@code current}를 읽은 뒤 아무도 바꾸지 않았을 때만 {@code next}로 바꾼다(저장 상태·만료 시각 조건).
     *
     * @return 바뀐 제안. 경합에 졌으면 비어 있음
     */
    Optional<PriceOffer> transition(PriceOffer current, PriceOffer next);

    /** 만료 시각이 지난 저장 {@code PENDING}을 {@code EXPIRED}로 원자 전이한다. */
    long expireStalePending(String listingId, String buyerId, Instant now);

    /** 아직 만료되지 않은 저장 {@code PENDING}이 있는가. */
    boolean existsValidPending(String listingId, String buyerId, Instant now);

    /** {@code since} 이후 이 매물에 이 구매자가 만든 제안의 생성 시각. 오래된 순 최대 {@code limit}건. */
    List<Instant> createdTimesSince(String listingId, String buyerId, Instant since, int limit);

    /** 방의 제안. 최신순. */
    List<PriceOffer> findByRoom(String roomId, int limit);

    /** 매물의 제안 중 이 계정이 구매자 또는 판매자인 것. 최신순. */
    List<PriceOffer> findByListingForParty(String listingId, String accountId, int limit);
}
