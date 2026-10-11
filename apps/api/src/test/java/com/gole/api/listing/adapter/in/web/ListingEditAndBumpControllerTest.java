package com.gole.api.listing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.listing.application.port.in.BumpListingUseCase;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.ReviseListingUseCase;
import com.gole.api.listing.application.port.in.ReviseListingUseCase.ReviseListingCommand;
import com.gole.api.listing.application.port.in.ReviseListingUseCase.RevisionResult;
import com.gole.api.listing.domain.exception.ListingBumpCooldownException;
import com.gole.api.listing.domain.exception.ListingConflictException;
import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 매물 수정(PUT)·끌올(POST /bump) HTTP 계약. (listing-edit-and-bump E1~E3, B1~B3, R1) */
class ListingEditAndBumpControllerTest {

    private static final String PHOTO_KEY = "images/123e4567-e89b-42d3-a456-426614174000.jpg";
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration COOLDOWN = Duration.ofHours(24);

    private MockMvc mvc;
    private ReviseListingUseCase revise;
    private BumpListingUseCase bump;
    private GetListingUseCase get;

    @BeforeEach
    void setUp() {
        revise = mock(ReviseListingUseCase.class);
        bump = mock(BumpListingUseCase.class);
        get = mock(GetListingUseCase.class);
        when(bump.bumpCooldown()).thenReturn(COOLDOWN);
        ListingController controller = new ListingController(null, get, null, null, null, null, revise, bump);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request, String accountId) {
        return request.requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
    }

    private static String body(String photoKeysJson) {
        return """
                {
                  "sellerId": "forged-seller",
                  "title": "에펠탑 10307 가격 조정",
                  "description": "미개봉",
                  "price": 250000,
                  "condition": "LIKE_NEW",
                  "completeness": "FULL_BOX",
                  "hasBox": true,
                  "hasManual": true,
                  "hasMissingParts": false,
                  "missingPartsNote": "",
                  "defectsNote": "",
                  "photoKeys": %s,
                  "catalogSetNumber": "75192",
                  "category": "minifig",
                  "interestTag": "icons"
                }
                """.formatted(photoKeysJson);
    }

    private static Listing revised() {
        return new Listing(
                "listing-1",
                "seller-1",
                "에펠탑 10307 가격 조정",
                "미개봉",
                Money.won(250_000),
                ItemCondition.LIKE_NEW,
                new ConditionDisclosure(Completeness.FULL_BOX, true, true, false, "", ""),
                List.of(PHOTO_KEY, "catalog/10307.svg"),
                "10307",
                ListingCategory.SET,
                InterestTag.ICONS,
                ListingStatus.ACTIVE,
                CREATED,
                null,
                null,
                Money.won(280_000),
                CREATED.plusSeconds(60));
    }

    @Test
    void putRevisesAsAuthenticatedSellerAndIgnoresSetCategoryAndBodySellerId() throws Exception {
        when(revise.revise(any())).thenReturn(new RevisionResult(revised(), true, 280_000));

        mvc.perform(asUser(put("/api/v1/listings/listing-1"), "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"" + PHOTO_KEY + "\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("listing-1"))
                .andExpect(jsonPath("$.price").value(250_000))
                .andExpect(jsonPath("$.previousPrice").value(280_000))
                .andExpect(jsonPath("$.priceChangedAt").exists())
                .andExpect(jsonPath("$.listedAt").exists())
                .andExpect(jsonPath("$.bumpedAt").doesNotExist())
                .andExpect(jsonPath("$.bumpAvailableAt").exists())
                .andExpect(jsonPath("$.catalogSetNumber").value("10307"))
                // 저장 키는 사용자 업로드만 돌려준다 — 데모 커버(catalog/*.svg)는 재제출하면 거부된다.
                .andExpect(jsonPath("$.photoKeys.length()").value(1))
                .andExpect(jsonPath("$.photoKeys[0]").value(PHOTO_KEY))
                .andExpect(jsonPath("$.photoUrls.length()").value(2));

        ArgumentCaptor<ReviseListingCommand> command = ArgumentCaptor.forClass(ReviseListingCommand.class);
        verify(revise).revise(command.capture());
        ReviseListingCommand sent = command.getValue();
        assertThat(sent.listingId()).isEqualTo("listing-1");
        assertThat(sent.sellerId()).isEqualTo("seller-1");
        assertThat(sent.price()).isEqualTo(250_000);
        assertThat(sent.condition()).isEqualTo(ItemCondition.LIKE_NEW);
        assertThat(sent.disclosure().completeness()).isEqualTo(Completeness.FULL_BOX);
        assertThat(sent.photoKeys()).containsExactly(PHOTO_KEY);
        assertThat(sent.interestTag()).isEqualTo(InterestTag.ICONS);
    }

    @Test
    void putResponseTimestampsFollowListedAtAndCooldown() throws Exception {
        when(revise.revise(any())).thenReturn(new RevisionResult(revised(), true, 280_000));

        String json = mvc.perform(asUser(put("/api/v1/listings/listing-1"), "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"" + PHOTO_KEY + "\"]")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // listedAt이 없던 매물은 createdAt으로 보고, 다음 끌올 가능 시각은 거기에 쿨다운을 더한다.
        assertThat(json).contains("\"listedAt\":\"2026-01-01T00:00:00Z\"");
        assertThat(json).contains("\"bumpAvailableAt\":\"2026-01-02T00:00:00Z\"");
    }

    @Test
    void putValidationMirrorsCreate() throws Exception {
        assertThat(putStatus(body("[]"))).isEqualTo(400);
        assertThat(putStatus(body("[" + "\"k\",".repeat(10) + "\"k\"]"))).isEqualTo(400);
        assertThat(putStatus(body("[\"" + PHOTO_KEY + "\"]").replace("\"price\": 250000", "\"price\": -1")))
                .isEqualTo(400);
        assertThat(putStatus(body("[\"" + PHOTO_KEY + "\"]").replace("\"에펠탑 10307 가격 조정\"", "\" \"")))
                .isEqualTo(400);
        assertThat(putStatus(body("[\"" + PHOTO_KEY + "\"]")
                        .replace("\"title\": \"에펠탑 10307 가격 조정\"", "\"title\": \"" + "가".repeat(121) + "\"")))
                .isEqualTo(400);
        verify(revise, never()).revise(any());
    }

    @Test
    void putUnknownInterestTagIs400() throws Exception {
        assertThat(putStatus(body("[\"" + PHOTO_KEY + "\"]").replace("\"icons\"", "\"no-such-theme\"")))
                .isEqualTo(400);
    }

    private int putStatus(String json) throws Exception {
        return mvc.perform(asUser(put("/api/v1/listings/listing-1"), "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void putMapsOwnershipAndStateErrors() throws Exception {
        when(revise.revise(any()))
                .thenThrow(new ForbiddenException("LISTING_ACCESS_DENIED", "본인의 매물만 처리할 수 있습니다"))
                .thenThrow(ListingConflictException.orderInProgress())
                .thenThrow(ListingConflictException.notEditable());

        expectPutError(403, "LISTING_ACCESS_DENIED");
        expectPutError(409, "LISTING_ORDER_IN_PROGRESS");
        expectPutError(409, "LISTING_NOT_EDITABLE");
    }

    private void expectPutError(int status, String code) throws Exception {
        mvc.perform(asUser(put("/api/v1/listings/listing-1"), "intruder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("[\"" + PHOTO_KEY + "\"]")))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void bumpReturnsListingWithNewListedAt() throws Exception {
        Instant bumpedAt = CREATED.plus(COOLDOWN);
        Listing bumped = new Listing(
                "listing-1",
                "seller-1",
                "에펠탑",
                "미개봉",
                Money.won(280_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of(PHOTO_KEY),
                "10307",
                ListingCategory.SET,
                null,
                ListingStatus.ACTIVE,
                CREATED,
                bumpedAt,
                bumpedAt,
                null,
                null);
        when(bump.bump("listing-1", "seller-1")).thenReturn(bumped);

        String json = mvc.perform(asUser(post("/api/v1/listings/listing-1/bump"), "seller-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPrice").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).contains("\"listedAt\":\"2026-01-02T00:00:00Z\"");
        assertThat(json).contains("\"bumpedAt\":\"2026-01-02T00:00:00Z\"");
        assertThat(json).contains("\"bumpAvailableAt\":\"2026-01-03T00:00:00Z\"");
        assertThat(json).contains("\"createdAt\":\"2026-01-01T00:00:00Z\"");
    }

    @Test
    void bumpWithinCooldownIs429WithRetryAfterSeconds() throws Exception {
        when(bump.bump("listing-1", "seller-1"))
                .thenThrow(
                        new ListingBumpCooldownException(Duration.ofMinutes(90).plusMillis(1)));

        mvc.perform(asUser(post("/api/v1/listings/listing-1/bump"), "seller-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5401"))
                .andExpect(jsonPath("$.code").value("LISTING_BUMP_COOLDOWN"));
    }

    @Test
    void bumpMapsOwnershipAndStateErrors() throws Exception {
        when(bump.bump("listing-1", "intruder"))
                .thenThrow(new ForbiddenException("LISTING_ACCESS_DENIED", "본인의 매물만 처리할 수 있습니다"));
        when(bump.bump("reserved", "seller-1")).thenThrow(ListingConflictException.orderInProgress());
        when(bump.bump("sold", "seller-1")).thenThrow(ListingConflictException.notBumpable());

        mvc.perform(asUser(post("/api/v1/listings/listing-1/bump"), "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ACCESS_DENIED"));
        mvc.perform(asUser(post("/api/v1/listings/reserved/bump"), "seller-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_ORDER_IN_PROGRESS"));
        mvc.perform(asUser(post("/api/v1/listings/sold/bump"), "seller-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_BUMPABLE"));
    }

    @Test
    void publicDetailAlsoCarriesBumpAndPriceFields() throws Exception {
        when(get.getPublicById("listing-1")).thenReturn(revised());

        mvc.perform(get("/api/v1/listings/listing-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPrice").value(280_000))
                .andExpect(jsonPath("$.bumpAvailableAt").exists())
                .andExpect(jsonPath("$.photoKeys[0]").value(PHOTO_KEY));
    }
}
