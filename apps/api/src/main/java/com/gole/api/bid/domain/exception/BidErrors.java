package com.gole.api.bid.domain.exception;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;

/**
 * 구매 입찰 오류 코드 모음. 프론트가 코드로 분기하므로 코드 문자열은 스펙(buy-bids D2~D7)과 1:1로 고정한다.
 *
 * <p>{@code OfferErrors}와 같은 방식이다 — HTTP 상태는 공통 예외 종류가 정하고, 이 컨텍스트는 코드와 문구만 정한다.
 */
public final class BidErrors {

    public static final String SET_NOT_FOUND = "BID_SET_NOT_FOUND";
    public static final String PRICE_INVALID = "BID_PRICE_INVALID";
    public static final String DURATION_INVALID = "BID_DURATION_INVALID";
    public static final String CONDITION_INVALID = "BID_CONDITION_INVALID";
    public static final String LIMIT_EXCEEDED = "BID_LIMIT_EXCEEDED";
    public static final String ACCESS_DENIED = "BID_ACCESS_DENIED";
    public static final String NOT_ACTIVE = "BID_NOT_ACTIVE";
    public static final String LISTING_MISMATCH = "BID_LISTING_MISMATCH";
    public static final String NOT_FOUND = "BID_NOT_FOUND";
    public static final String LISTING_ACCESS_DENIED = "LISTING_ACCESS_DENIED";
    public static final String LISTING_ALREADY_FILLED = "BID_LISTING_ALREADY_FILLED";

    private BidErrors() {}

    public static NotFoundException setNotFound() {
        return new NotFoundException(SET_NOT_FOUND, "카탈로그에 없는 세트에는 입찰할 수 없습니다");
    }

    public static BadRequestException priceInvalid() {
        return new BadRequestException(PRICE_INVALID, "입찰가는 1원 이상 1억 원 이하여야 합니다");
    }

    public static BadRequestException durationInvalid() {
        return new BadRequestException(DURATION_INVALID, "입찰 기간은 7일·30일·60일 중 하나여야 합니다");
    }

    public static BadRequestException conditionInvalid() {
        return new BadRequestException(CONDITION_INVALID, "알 수 없는 상태 등급입니다");
    }

    public static ConflictException limitExceeded() {
        return new ConflictException(LIMIT_EXCEEDED, "진행 중인 입찰은 30건까지 걸 수 있습니다");
    }

    public static ForbiddenException accessDenied() {
        return new ForbiddenException(ACCESS_DENIED, "본인의 입찰만 취소할 수 있습니다");
    }

    public static ConflictException notActive() {
        return new ConflictException(NOT_ACTIVE, "진행 중인 입찰이 아닙니다");
    }

    /** 취소할 입찰 자체가 없다. 판매자 즉시 판매의 "맞는 입찰 없음"(409)과 코드는 같지만 상태가 다르다. */
    public static NotFoundException bidMissing() {
        return new NotFoundException(NOT_FOUND, "입찰을 찾을 수 없습니다");
    }

    /** 판매자 즉시 판매 시 그 세트·상태에 받을 수 있는 입찰이 없다. (D7) */
    public static ConflictException noMatchingBid() {
        return new ConflictException(NOT_FOUND, "이 세트·상태에 받을 수 있는 입찰이 없습니다");
    }

    public static ConflictException listingMismatch() {
        return new ConflictException(LISTING_MISMATCH, "이 세트의 매물이 아니라 입찰을 받을 수 없습니다");
    }

    public static ConflictException listingNotActive() {
        return new ConflictException(LISTING_MISMATCH, "판매 중인 매물만 입찰가에 팔 수 있습니다");
    }

    /** 이 매물로 이미 체결한 입찰의 수락 제안이 아직 살아 있다. 매물 하나는 입찰 하나만 받는다. */
    public static ConflictException listingAlreadyFilled() {
        return new ConflictException(LISTING_ALREADY_FILLED, "이 매물은 이미 입찰자와 체결돼 진행 중입니다. 그 제안이 끝나면 다음 입찰을 받을 수 있습니다");
    }

    public static ForbiddenException listingAccessDenied() {
        return new ForbiddenException(LISTING_ACCESS_DENIED, "본인의 매물만 처리할 수 있습니다");
    }
}
