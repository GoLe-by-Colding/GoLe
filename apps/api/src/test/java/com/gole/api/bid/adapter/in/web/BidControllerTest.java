package com.gole.api.bid.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.bid.application.port.in.CancelBidUseCase;
import com.gole.api.bid.application.port.in.FillBidUseCase;
import com.gole.api.bid.application.port.in.FillBidUseCase.FillResult;
import com.gole.api.bid.application.port.in.GetBidBookUseCase;
import com.gole.api.bid.application.port.in.ListMyBidsUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase;
import com.gole.api.bid.application.port.in.PlaceBidUseCase.PlaceBidCommand;
import com.gole.api.bid.domain.exception.BidErrors;
import com.gole.api.bid.domain.model.Bid;
import com.gole.api.bid.domain.model.BidBook;
import com.gole.api.bid.domain.model.BidCondition;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 구매 입찰 HTTP 계약. (buy-bids D2~D7) */
class BidControllerTest {

    private static final Instant T0 = Instant.parse("2026-03-11T00:00:00Z");

    private MockMvc mvc;
    private PlaceBidUseCase place;
    private CancelBidUseCase cancel;
    private ListMyBidsUseCase mine;
    private GetBidBookUseCase book;
    private FillBidUseCase fill;

    @BeforeEach
    void setUp() {
        place = mock(PlaceBidUseCase.class);
        cancel = mock(CancelBidUseCase.class);
        mine = mock(ListMyBidsUseCase.class);
        book = mock(GetBidBookUseCase.class);
        fill = mock(FillBidUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new BidController(place, cancel, mine, book, fill))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    private static Bid bid() {
        return Bid.place("bid-1", "buyer-1", "10307", BidCondition.LIKE_NEW, 250_000, 30, T0);
    }

    @Test
    @DisplayName("입찰 걸기는 세션 계정으로 위임하고 200과 소문자 상태·키를 돌려준다")
    void place_returnsBidResponse() throws Exception {
        when(place.place(any())).thenReturn(bid());

        mvc.perform(post("/api/v1/bids")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setNumber\":\"10307\",\"condition\":\"like_new\",\"price\":250000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bid-1"))
                .andExpect(jsonPath("$.condition").value("like_new"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.durationDays").value(30))
                .andExpect(jsonPath("$.filledListingId").doesNotExist());

        ArgumentCaptor<PlaceBidCommand> command = ArgumentCaptor.forClass(PlaceBidCommand.class);
        verify(place).place(command.capture());
        assertThat(command.getValue()).isEqualTo(new PlaceBidCommand("buyer-1", "10307", "like_new", 250_000, null));
    }

    @Test
    @DisplayName("가격이 없으면 도메인까지 가지 않고 BID_PRICE_INVALID다")
    void place_missingPriceIs400() throws Exception {
        mvc.perform(post("/api/v1/bids")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setNumber\":\"10307\",\"condition\":\"like_new\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BidErrors.PRICE_INVALID));
        verify(place, never()).place(any());
    }

    @Test
    @DisplayName("오류 코드는 스펙의 HTTP 상태로 나간다")
    void errors_mapToSpecStatuses() throws Exception {
        when(place.place(any()))
                .thenThrow(BidErrors.setNotFound())
                .thenThrow(BidErrors.limitExceeded())
                .thenThrow(BidErrors.conditionInvalid());
        String body = "{\"setNumber\":\"10307\",\"condition\":\"like_new\",\"price\":1000}";
        int[] expected = {404, 409, 400};
        String[] codes = {BidErrors.SET_NOT_FOUND, BidErrors.LIMIT_EXCEEDED, BidErrors.CONDITION_INVALID};
        for (int i = 0; i < expected.length; i++) {
            mvc.perform(post("/api/v1/bids")
                            .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is(expected[i]))
                    .andExpect(jsonPath("$.code").value(codes[i]));
        }

        doThrow(BidErrors.accessDenied()).when(cancel).cancel("bid-1", "intruder");
        mvc.perform(delete("/api/v1/bids/bid-1").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(BidErrors.ACCESS_DENIED));
        mvc.perform(delete("/api/v1/bids/bid-1").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isNoContent());

        when(fill.fill("10307", "listing-1", "seller-1")).thenThrow(BidErrors.noMatchingBid());
        mvc.perform(post("/api/v1/bids/book/10307/fill")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"listing-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(BidErrors.NOT_FOUND));
    }

    @Test
    @DisplayName("호가창은 상태 다섯 개를 담고 입찰자 식별자를 내보내지 않는다")
    void book_isPublicAndAnonymous() throws Exception {
        when(book.book("10307"))
                .thenReturn(BidBook.of(
                        "10307",
                        List.of(bid(), Bid.place("bid-2", "buyer-2", "10307", BidCondition.LIKE_NEW, 260_000, 30, T0)),
                        T0));

        String json = mvc.perform(get("/api/v1/bids/book/10307"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions.length()").value(5))
                .andExpect(jsonPath("$.conditions[0].condition").value("new_sealed"))
                .andExpect(jsonPath("$.conditions[0].highestPrice").doesNotExist())
                .andExpect(jsonPath("$.conditions[1].highestPrice").value(260_000))
                .andExpect(jsonPath("$.conditions[1].bidCount").value(2))
                .andExpect(jsonPath("$.conditions[1].levels[0].price").value(260_000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json).doesNotContain("buyer-1").doesNotContain("bidderId");
    }

    @Test
    @DisplayName("즉시 판매는 판매자 세션으로 위임하고 입찰가·제안·매물을 돌려준다")
    void fill_returnsOfferReference() throws Exception {
        when(fill.fill("10307", "listing-1", "seller-1"))
                .thenReturn(new FillResult("bid-1", 250_000, "offer-1", "listing-1"));

        mvc.perform(post("/api/v1/bids/book/10307/fill")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":\"listing-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidPrice").value(250_000))
                .andExpect(jsonPath("$.offerId").value("offer-1"))
                .andExpect(jsonPath("$.listingId").value("listing-1"));

        mvc.perform(post("/api/v1/bids/book/10307/fill")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 입찰은 세션 계정 기준이다")
    void mine_usesSessionAccount() throws Exception {
        when(mine.mine("buyer-1")).thenReturn(List.of(bid()));

        mvc.perform(get("/api/v1/bids/mine").requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].setNumber").value("10307"));
    }
}
