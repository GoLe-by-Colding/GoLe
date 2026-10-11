package com.gole.api.offer.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OfferRequests {

    private OfferRequests() {}

    /**
     * @param roomId 매물 채팅방
     * @param price 제안 가격(원). 범위({@code 0 < price < 매물가})는 서비스가 {@code OFFER_PRICE_INVALID}로 검사한다
     */
    public record MakeOfferRequest(
            @NotBlank @Size(max = 100) String roomId,
            @NotNull Long price) {}
}
