package com.gole.api.offer.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.account.adapter.in.web.RequiresOnboarding;
import com.gole.api.account.application.port.in.ManageThirdPartyProvisionConsentUseCase;
import com.gole.api.offer.adapter.in.web.OfferRequests.MakeOfferRequest;
import com.gole.api.offer.application.port.in.ListOffersUseCase;
import com.gole.api.offer.application.port.in.MakeOfferUseCase;
import com.gole.api.offer.application.port.in.MakeOfferUseCase.MakeOfferCommand;
import com.gole.api.offer.application.port.in.RespondToOfferUseCase;
import com.gole.api.offer.domain.exception.OfferErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 가격 제안. (price-offer O1~O15)
 *
 * <p>출시 단계와 무관하게 열려 있다(O20) — {@code LaunchGateInterceptor}는 주문·후기 경로에만 걸린다.
 * 조회도 세션이 필요하다({@code UserAuthInterceptor} 비공개 조회 접두사).
 */
@Tag(name = "Offer", description = "가격 제안(네고) — 제안·수락·거절·철회·조회")
@RestController
@RequestMapping("/api/v1/offers")
public class OfferController {

    private final MakeOfferUseCase makeOffer;
    private final RespondToOfferUseCase respondToOffer;
    private final ListOffersUseCase listOffers;
    private final ManageThirdPartyProvisionConsentUseCase thirdPartyProvisionConsents;

    public OfferController(
            MakeOfferUseCase makeOffer,
            RespondToOfferUseCase respondToOffer,
            ListOffersUseCase listOffers,
            ManageThirdPartyProvisionConsentUseCase thirdPartyProvisionConsents) {
        this.makeOffer = makeOffer;
        this.respondToOffer = respondToOffer;
        this.listOffers = listOffers;
        this.thirdPartyProvisionConsents = thirdPartyProvisionConsents;
    }

    @Operation(summary = "가격 제안", description = "매물 채팅방 구매자만. 0 < price < 매물가. 방에 제안 메시지를 남기고 판매자에게 알린다.")
    @PostMapping
    @RequiresOnboarding // O2: 채팅 대화 시작·주문과 같은 거래성 액션
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse make(@Valid @RequestBody MakeOfferRequest request, HttpServletRequest http) {
        String actorId = AuthenticatedUser.id(http);
        // O2: 채팅 메시지 전송과 같은 제3자 제공 동의. 방·판매자 검사는 서비스가 한다.
        thirdPartyProvisionConsents.requireCurrent(actorId);
        return OfferResponse.from(makeOffer.make(new MakeOfferCommand(request.roomId(), actorId, request.price())));
    }

    @Operation(summary = "제안 수락", description = "판매자만. 만료를 수락 시각 + 수락 TTL로 다시 잡는다.")
    @PostMapping("/{offerId}/accept")
    public OfferResponse accept(@PathVariable String offerId, HttpServletRequest http) {
        return OfferResponse.from(respondToOffer.accept(offerId, AuthenticatedUser.id(http)));
    }

    @Operation(summary = "제안 거절", description = "판매자만. 대기 제안은 거절, 수락한 제안은 수락 취소가 된다.")
    @PostMapping("/{offerId}/decline")
    public OfferResponse decline(@PathVariable String offerId, HttpServletRequest http) {
        return OfferResponse.from(respondToOffer.decline(offerId, AuthenticatedUser.id(http)));
    }

    @Operation(summary = "제안 철회", description = "구매자만. 대기·수락 제안을 거둔다.")
    @PostMapping("/{offerId}/withdraw")
    public OfferResponse withdraw(@PathVariable String offerId, HttpServletRequest http) {
        return OfferResponse.from(respondToOffer.withdraw(offerId, AuthenticatedUser.id(http)));
    }

    @Operation(summary = "제안 조회", description = "roomId 또는 listingId 중 하나. 방은 참여자만, 매물은 판매자면 전부·아니면 내 제안만. 최신순 최대 50건.")
    @GetMapping
    public List<OfferResponse> list(
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String listingId,
            HttpServletRequest http) {
        boolean byRoom = roomId != null && !roomId.isBlank();
        boolean byListing = listingId != null && !listingId.isBlank();
        if (byRoom == byListing) {
            throw OfferErrors.queryRequired();
        }
        String actorId = AuthenticatedUser.id(http);
        var offers = byRoom ? listOffers.byRoom(roomId, actorId) : listOffers.byListing(listingId, actorId);
        return offers.stream().map(OfferResponse::from).toList();
    }
}
