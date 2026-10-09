package com.gole.api.bid.adapter.in.web;

import com.gole.api.bid.application.port.in.FillBidUseCase.FillResult;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidBook;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** 구매 입찰 요청·응답 DTO. (buy-bids D2~D7) */
public final class BidDtos {

    private BidDtos() {}

    /**
     * 검증은 도메인이 한다 — 오류 코드가 스펙의 {@code BID_*}로 나가야 하기 때문이다. 빈 값 검증(@NotBlank 등)을
     * 여기 두면 {@code VALIDATION_FAILED}로 먼저 끝나 프론트가 코드로 분기할 수 없다.
     *
     * @param durationDays 7·30·60. 없으면 30
     */
    public record PlaceBidRequest(String setNumber, String condition, Long price, Integer durationDays) {}

    public record FillBidRequest(String listingId) {}

    /**
     * @param status 만료를 반영한 유효 상태(active·canceled·filled·expired)
     * @param filledListingId 체결된 매물. 체결 전이면 null
     * @param offerId 체결로 생긴 수락 제안. 체결 전이면 null
     */
    public record BidResponse(
            String id,
            String setNumber,
            String condition,
            long price,
            int durationDays,
            String status,
            Instant createdAt,
            Instant placedAt,
            Instant expiresAt,
            String filledListingId,
            String offerId) {

        static BidResponse from(Bid bid) {
            return new BidResponse(
                    bid.id(),
                    bid.setNumber(),
                    bid.condition().key(),
                    bid.price(),
                    bid.durationDays(),
                    bid.status().name().toLowerCase(Locale.ROOT),
                    bid.createdAt(),
                    bid.placedAt(),
                    bid.expiresAt(),
                    bid.filledListingId(),
                    bid.offerId());
        }
    }

    /** 공개 호가창. 입찰자 식별자를 담지 않는다. (D6) */
    public record BidBookResponse(String setNumber, List<ConditionBookResponse> conditions) {

        static BidBookResponse from(BidBook book) {
            return new BidBookResponse(
                    book.setNumber(),
                    book.conditions().stream()
                            .map(condition -> new ConditionBookResponse(
                                    condition.condition().key(),
                                    condition.highestPrice(),
                                    condition.bidCount(),
                                    condition.levels().stream()
                                            .map(level -> new LevelResponse(level.price(), level.count()))
                                            .toList()))
                            .toList());
        }
    }

    /** @param highestPrice 최고 입찰가 = 지금 팔면 받는 값. 없으면 null */
    public record ConditionBookResponse(
            String condition, Long highestPrice, int bidCount, List<LevelResponse> levels) {}

    public record LevelResponse(long price, int count) {}

    public record FillBidResponse(long bidPrice, String offerId, String listingId) {

        static FillBidResponse from(FillResult result) {
            return new FillBidResponse(result.bidPrice(), result.offerId(), result.listingId());
        }
    }
}
