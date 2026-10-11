package com.gole.api.offer.domain.exception;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.common.exception.TooManyRequestsException;
import java.time.Duration;

/**
 * 가격 제안 오류 코드 모음. 프론트가 코드로 분기하므로 코드 문자열은 스펙(price-offer O3~O10)과 1:1로 고정한다.
 *
 * <p>코드마다 예외 클래스를 두지 않고 공통 예외 계열을 돌려준다. HTTP 상태는 공통 예외 종류가 정하고
 * ({@code GlobalExceptionHandler}), 이 컨텍스트가 정하는 것은 코드와 문구뿐이다.
 */
public final class OfferErrors {

    public static final String ROOM_NOT_LISTING = "OFFER_ROOM_NOT_LISTING";
    public static final String BUYER_ONLY = "OFFER_BUYER_ONLY";
    public static final String LISTING_UNAVAILABLE = "OFFER_LISTING_UNAVAILABLE";
    public static final String PRICE_INVALID = "OFFER_PRICE_INVALID";
    public static final String ALREADY_PENDING = "OFFER_ALREADY_PENDING";
    public static final String RATE_LIMITED = "OFFER_RATE_LIMITED";
    public static final String NOT_PENDING = "OFFER_NOT_PENDING";
    public static final String NOT_OPEN = "OFFER_NOT_OPEN";
    public static final String ACCESS_DENIED = "OFFER_ACCESS_DENIED";
    public static final String NOT_FOUND = "OFFER_NOT_FOUND";
    public static final String QUERY_REQUIRED = "OFFER_QUERY_REQUIRED";

    private OfferErrors() {}

    public static BadRequestException roomNotListing() {
        return new BadRequestException(ROOM_NOT_LISTING, "매물 대화방에서만 가격을 제안할 수 있습니다");
    }

    public static ForbiddenException buyerOnly() {
        return new ForbiddenException(BUYER_ONLY, "구매자만 가격을 제안할 수 있습니다");
    }

    public static ConflictException listingUnavailable() {
        return new ConflictException(LISTING_UNAVAILABLE, "판매 중인 매물이 아니라 가격 제안을 진행할 수 없습니다");
    }

    public static BadRequestException priceInvalid() {
        return new BadRequestException(PRICE_INVALID, "제안 가격은 0원보다 크고 판매가보다 낮아야 합니다");
    }

    public static ConflictException alreadyPending() {
        return new ConflictException(ALREADY_PENDING, "이 매물에 응답을 기다리는 제안이 이미 있습니다");
    }

    public static TooManyRequestsException rateLimited(Duration retryAfter) {
        return new TooManyRequestsException(RATE_LIMITED, "같은 매물에는 하루에 제안할 수 있는 횟수를 넘었습니다", retryAfter);
    }

    public static ConflictException notPending() {
        return new ConflictException(NOT_PENDING, "응답을 기다리는 제안이 아닙니다");
    }

    public static ConflictException notOpen() {
        return new ConflictException(NOT_OPEN, "이미 끝난 제안입니다");
    }

    public static ForbiddenException accessDenied() {
        return new ForbiddenException(ACCESS_DENIED, "이 제안에 대한 권한이 없습니다");
    }

    public static NotFoundException notFound() {
        return new NotFoundException(NOT_FOUND, "제안을 찾을 수 없습니다");
    }

    public static BadRequestException queryRequired() {
        return new BadRequestException(QUERY_REQUIRED, "roomId 또는 listingId 중 하나만 지정해 주세요");
    }
}
