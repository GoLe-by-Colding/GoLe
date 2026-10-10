package com.gole.api.offer.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.account.application.port.in.ManageThirdPartyProvisionConsentUseCase;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.offer.application.port.in.ListOffersUseCase;
import com.gole.api.offer.application.port.in.MakeOfferUseCase;
import com.gole.api.offer.application.port.in.MakeOfferUseCase.MakeOfferCommand;
import com.gole.api.offer.application.port.in.RespondToOfferUseCase;
import com.gole.api.offer.domain.exception.OfferErrors;
import com.gole.api.offer.domain.model.OfferOrigin;
import com.gole.api.offer.domain.model.OfferStatus;
import com.gole.api.offer.domain.model.PriceOffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OfferControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    private final MakeOfferUseCase makeOffer = mock(MakeOfferUseCase.class);
    private final RespondToOfferUseCase respond = mock(RespondToOfferUseCase.class);
    private final ListOffersUseCase list = mock(ListOffersUseCase.class);
    private final ManageThirdPartyProvisionConsentUseCase consents =
            mock(ManageThirdPartyProvisionConsentUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OfferController(makeOffer, respond, list, consents))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    private static PriceOffer pending() {
        return PriceOffer.propose(
                "offer-1", "listing-1", "room-1", "buyer-1", "seller-1", 250_000, 280_000, T0, Duration.ofHours(48));
    }

    @Test
    @DisplayName("제안 생성은 201과 스펙의 응답 필드를 돌려준다")
    void make_returnsCreatedOfferResponse() throws Exception {
        when(makeOffer.make(new MakeOfferCommand("room-1", "buyer-1", 250_000))).thenReturn(pending());

        mvc.perform(post("/api/v1/offers")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room-1\",\"price\":250000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("offer-1"))
                .andExpect(jsonPath("$.listingId").value("listing-1"))
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.buyerId").value("buyer-1"))
                .andExpect(jsonPath("$.sellerId").value("seller-1"))
                .andExpect(jsonPath("$.price").value(250_000))
                .andExpect(jsonPath("$.listingPriceAtOffer").value(280_000))
                .andExpect(jsonPath("$.origin").value("chat"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.respondedAt").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").value("2026-10-03T00:00:00Z"));
        verify(consents).requireCurrent("buyer-1");
    }

    @Test
    @DisplayName("제3자 제공 동의가 없으면 제안을 만들지 않는다")
    void make_requiresCurrentThirdPartyConsent() throws Exception {
        doThrow(new ForbiddenException(ManageThirdPartyProvisionConsentUseCase.REQUIRED_CODE, "consent required"))
                .when(consents)
                .requireCurrent("buyer-1");

        mvc.perform(post("/api/v1/offers")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room-1\",\"price\":250000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ManageThirdPartyProvisionConsentUseCase.REQUIRED_CODE));
        verify(makeOffer, never()).make(any());
    }

    @Test
    @DisplayName("서비스 오류 코드는 스펙의 HTTP 상태로 나간다")
    void errors_mapToSpecStatuses() throws Exception {
        when(makeOffer.make(any())).thenThrow(OfferErrors.priceInvalid());
        mvc.perform(post("/api/v1/offers")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room-1\",\"price\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OfferErrors.PRICE_INVALID));

        doThrow(OfferErrors.rateLimited(Duration.ofHours(19))).when(makeOffer).make(any());
        mvc.perform(post("/api/v1/offers")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room-1\",\"price\":1000}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "68400"))
                .andExpect(jsonPath("$.code").value(OfferErrors.RATE_LIMITED));

        when(respond.accept("offer-1", "buyer-1")).thenThrow(OfferErrors.accessDenied());
        mvc.perform(post("/api/v1/offers/offer-1/accept").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(OfferErrors.ACCESS_DENIED));

        when(respond.accept("missing", "seller-1")).thenThrow(OfferErrors.notFound());
        mvc.perform(post("/api/v1/offers/missing/accept").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(OfferErrors.NOT_FOUND));

        when(respond.withdraw("offer-1", "buyer-1")).thenThrow(OfferErrors.notOpen());
        mvc.perform(post("/api/v1/offers/offer-1/withdraw").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(OfferErrors.NOT_OPEN));
    }

    @Test
    @DisplayName("수락·거절·철회는 행위자 식별자로 위임하고 유효 상태를 돌려준다")
    void respond_delegatesWithActor() throws Exception {
        PriceOffer accepted = pending().accept(T0.plusSeconds(60), Duration.ofHours(72));
        when(respond.accept("offer-1", "seller-1")).thenReturn(accepted);
        when(respond.decline("offer-1", "seller-1")).thenReturn(accepted.decline(T0.plusSeconds(120)));
        when(respond.withdraw("offer-1", "buyer-1")).thenReturn(pending().withdraw(T0.plusSeconds(30)));

        mvc.perform(post("/api/v1/offers/offer-1/accept").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.respondedAt").value("2026-10-01T00:01:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-04T00:01:00Z"));
        mvc.perform(post("/api/v1/offers/offer-1/decline").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("declined"));
        mvc.perform(post("/api/v1/offers/offer-1/withdraw").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("withdrawn"));
    }

    @Test
    @DisplayName("조회는 roomId 또는 listingId 중 정확히 하나를 받는다")
    void list_requiresExactlyOneQuery() throws Exception {
        PriceOffer bidOffer = PriceOffer.preAccepted(
                "offer-2",
                "listing-1",
                "buyer-1",
                "seller-1",
                300_000,
                280_000,
                OfferOrigin.BID,
                T0,
                Duration.ofHours(72));
        when(list.byRoom("room-1", "buyer-1")).thenReturn(List.of(pending()));
        when(list.byListing("listing-1", "seller-1")).thenReturn(List.of(bidOffer, pending()));

        mvc.perform(get("/api/v1/offers")
                        .param("roomId", "room-1")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("offer-1"));
        mvc.perform(get("/api/v1/offers")
                        .param("listingId", "listing-1")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origin").value("bid"))
                .andExpect(jsonPath("$[0].roomId").doesNotExist())
                .andExpect(jsonPath("$[0].status").value(OfferStatus.ACCEPTED.key()))
                .andExpect(jsonPath("$[1].status").value("pending"));

        mvc.perform(get("/api/v1/offers").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OfferErrors.QUERY_REQUIRED));
        mvc.perform(get("/api/v1/offers")
                        .param("roomId", "room-1")
                        .param("listingId", "listing-1")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(OfferErrors.QUERY_REQUIRED));
    }

    @Test
    @DisplayName("roomId·price가 없으면 400 VALIDATION_ERROR")
    void make_validatesBody() throws Exception {
        mvc.perform(post("/api/v1/offers")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"room-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(makeOffer, never()).make(any());
    }
}
