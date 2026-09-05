package com.gole.api.listing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.account.application.service.SellerIdentityVerificationService;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.listing.adapter.out.persistence.ListingCommentDocument;
import com.gole.api.listing.adapter.out.persistence.ListingCommentMongoRepository;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import com.gole.api.notification.application.port.in.NotifyUseCase;
import com.gole.api.notification.application.port.in.NotifyUseCase.NotifyCommand;
import com.gole.api.notification.domain.model.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ListingCommentControllerTest {

    private final ListingCommentMongoRepository comments = mock(ListingCommentMongoRepository.class);
    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final NotifyUseCase notifications = mock(NotifyUseCase.class);
    private final SellerIdentityVerificationService sellerIdentity = mock(SellerIdentityVerificationService.class);
    private final ListingCommentController controller =
            new ListingCommentController(comments, listings, notifications, sellerIdentity);

    @Test
    void listDoesNotReadCommentsWhenListingIsHidden() {
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));

        assertThatThrownBy(() -> controller.list("deleted-listing")).isInstanceOf(ListingNotFoundException.class);
        verifyNoInteractions(comments);
    }

    @Test
    void createDoesNotSaveOrNotifyWhenListingIsHidden() {
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));

        assertThatThrownBy(() -> controller.create(
                        "deleted-listing",
                        new ListingCommentController.CreateCommentRequest("forged-author", "구매 가능한가요?"),
                        authenticated("buyer-1")))
                .isInstanceOf(ListingNotFoundException.class);
        verify(comments, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void hiddenListingCommentsReturnNotFoundAtHttpBoundaryWithoutWrites() throws Exception {
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();

        mvc.perform(get("/api/v1/listings/deleted-listing/comments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mvc.perform(post("/api/v1/listings/deleted-listing/comments")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"구매 가능한가요?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));

        verify(comments, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void createUsesAuthenticatedAuthorAndNotifiesVisibleListingSeller() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        when(comments.save(any(ListingCommentDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.create(
                "listing-1",
                new ListingCommentController.CreateCommentRequest("forged-author", "구매 가능한가요?"),
                authenticated("buyer-1"));

        assertThat(response.authorId()).isEqualTo("buyer-1");
        verify(sellerIdentity).requireVerifiedSeller("seller-1");
        ArgumentCaptor<NotifyCommand> command = ArgumentCaptor.forClass(NotifyCommand.class);
        verify(notifications).notify(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new NotifyCommand(
                        "seller-1", NotificationType.COMMENT, "매물 '에펠탑 10307'에 문의가 달렸어요.", "/listings/listing-1"));
    }

    @Test
    void createFailsClosedBeforeWriteWhenListingSellerIdentityIsNotReady() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        doThrow(new ServiceUnavailableException("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "판매자 신원확인 준비 중"))
                .when(sellerIdentity)
                .requireVerifiedSeller("seller-1");

        assertThatThrownBy(() -> controller.create(
                        "listing-1",
                        new ListingCommentController.CreateCommentRequest("forged-author", "구매 가능한가요?"),
                        authenticated("buyer-1")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(comments, never()).save(any());
        verifyNoInteractions(notifications);
    }

    private static Listing listing(String sellerId) {
        return new Listing(
                "listing-1",
                sellerId,
                "에펠탑 10307",
                "미개봉",
                Money.won(280_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("photo.jpg"),
                "10307",
                ListingCategory.SET,
                ListingStatus.ACTIVE,
                Instant.parse("2026-08-30T00:00:00Z"));
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }
}
