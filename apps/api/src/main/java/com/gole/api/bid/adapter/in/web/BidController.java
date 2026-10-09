package com.gole.api.bid.adapter.in.web;

import com.gole.api.bid.adapter.in.web.BidDtos.BidBookResponse;
import com.gole.api.bid.adapter.in.web.BidDtos.BidResponse;
import com.gole.api.bid.adapter.in.web.BidDtos.FillBidRequest;
import com.gole.api.bid.adapter.in.web.BidDtos.FillBidResponse;
import com.gole.api.bid.adapter.in.web.BidDtos.PlaceBidRequest;
import com.gole.api.bid.application.port.in.CancelBidUseCase;
import com.gole.api.bid.application.port.in.FillBidUseCase;
import com.gole.api.bid.application.port.in.GetBidBookUseCase;
import com.gole.api.bid.application.port.in.ListMyBidsUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.common.web.auth.RequiresOnboarding;
import com.gole.api.common.web.auth.RequiresVerifiedSellerIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 구매 입찰. use case 인터페이스에만 의존한다. (buy-bids D2~D7)
 *
 * <p>호가창은 공개다. {@code /mine}은 {@code UserAuthInterceptor}가 세션을 강제하고, 쓰기는 GET이 아니라서 원래
 * 세션이 필요하다. 출시 단계와 무관하게 열려 있다(D10) — {@code LaunchGateInterceptor}는 주문·후기 경로에만 걸린다.
 */
@Tag(name = "Bid", description = "구매 입찰 걸기·취소·호가창·판매자 즉시 판매")
@RestController
@RequestMapping("/api/v1/bids")
public class BidController {

    private final PlaceBidUseCase placeBid;
    private final CancelBidUseCase cancelBid;
    private final ListMyBidsUseCase listMyBids;
    private final GetBidBookUseCase getBidBook;
    private final FillBidUseCase fillBid;

    public BidController(
            PlaceBidUseCase placeBid,
            CancelBidUseCase cancelBid,
            ListMyBidsUseCase listMyBids,
            GetBidBookUseCase getBidBook,
            FillBidUseCase fillBid) {
        this.placeBid = placeBid;
        this.cancelBid = cancelBid;
        this.listMyBids = listMyBids;
        this.getBidBook = getBidBook;
        this.fillBid = fillBid;
    }

    @Operation(
            summary = "입찰 걸기",
            description = "세트·상태·가격으로 구매 입찰을 겁니다. 같은 세트·상태의 진행 중 입찰이 있으면 가격·기간만 갱신합니다.\n\n"
                    + "- 400 `BID_PRICE_INVALID`(1~100,000,000원)·`BID_DURATION_INVALID`(7·30·60일)·`BID_CONDITION_INVALID`\n"
                    + "- 404 `BID_SET_NOT_FOUND`, 409 `BID_LIMIT_EXCEEDED`(진행 중 30건)")
    @PostMapping
    @RequiresOnboarding
    public BidResponse place(@RequestBody PlaceBidRequest request, HttpServletRequest http) {
        if (request.price() == null) {
            throw BidErrors.priceInvalid();
        }
        return BidResponse.from(placeBid.place(new PlaceBidCommand(
                AuthenticatedUser.id(http),
                request.setNumber(),
                request.condition(),
                request.price(),
                request.durationDays())));
    }

    @Operation(summary = "입찰 취소", description = "본인의 진행 중 입찰만. 403 `BID_ACCESS_DENIED`, 409 `BID_NOT_ACTIVE`")
    @DeleteMapping("/{bidId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String bidId, HttpServletRequest http) {
        cancelBid.cancel(bidId, AuthenticatedUser.id(http));
    }

    @Operation(summary = "내 입찰", description = "최신순 최대 100건. status는 만료를 반영한 유효 상태입니다.")
    @GetMapping("/mine")
    public List<BidResponse> mine(HttpServletRequest http) {
        return listMyBids.mine(AuthenticatedUser.id(http)).stream()
                .map(BidResponse::from)
                .toList();
    }

    @Operation(summary = "세트 구매 호가창", description = "상태 다섯 개의 최고 입찰가(= 지금 팔면 받는 값)·입찰 수·상위 5단. 입찰자는 공개하지 않습니다.")
    @GetMapping("/book/{setNumber}")
    public BidBookResponse book(@PathVariable String setNumber) {
        return BidBookResponse.from(getBidBook.book(setNumber));
    }

    @Operation(
            summary = "최고 입찰가에 바로 판매",
            description =
                    "본인의 판매 중 매물을 그 세트·상태의 최고 입찰가에 팝니다. 입찰자에게 72시간 유효한 수락 제안이 생깁니다.\n\n"
                            + "- 403 `LISTING_ACCESS_DENIED`, 409 `BID_LISTING_MISMATCH`(다른 세트·판매 중 아님)·`BID_NOT_FOUND`(받을 입찰 없음)")
    @PostMapping("/book/{setNumber}/fill")
    @RequiresOnboarding
    @RequiresVerifiedSellerIdentity
    public FillBidResponse fill(
            @PathVariable String setNumber, @RequestBody FillBidRequest request, HttpServletRequest http) {
        if (request.listingId() == null || request.listingId().isBlank()) {
            throw new BadRequestException("INVALID_PARAMETER", "listingId가 필요합니다");
        }
        return FillBidResponse.from(fillBid.fill(setNumber, request.listingId(), AuthenticatedUser.id(http)));
    }
}
