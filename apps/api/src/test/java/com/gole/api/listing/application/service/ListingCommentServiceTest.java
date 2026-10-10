package com.gole.api.listing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.ServiceUnavailableException;
import com.gole.api.listing.application.port.in.GetListingUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase.PostListingCommentCommand;
import com.gole.api.listing.application.port.out.ListingCommentNotifierPort;
import com.gole.api.listing.application.port.out.ListingCommentRepositoryPort;
import com.gole.api.listing.application.port.out.ListingSellerVerificationPort;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.ListingComment;
import com.gole.api.listing.domain.model.ListingStatus;
import com.gole.api.listing.domain.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingCommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    private final GetListingUseCase listings = mock(GetListingUseCase.class);
    private final ListingCommentRepositoryPort comments = mock(ListingCommentRepositoryPort.class);
    private final ListingCommentNotifierPort notifier = mock(ListingCommentNotifierPort.class);
    private final ListingSellerVerificationPort sellerVerification = mock(ListingSellerVerificationPort.class);
    private final ListingCommentService service = new ListingCommentService(
            listings, comments, notifier, sellerVerification, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("숨김·삭제 매물이면 댓글을 읽지 않고 404 예외를 그대로 낸다")
    void list_doesNotReadCommentsWhenListingIsHidden() {
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));

        assertThatThrownBy(() -> service.list("deleted-listing")).isInstanceOf(ListingNotFoundException.class);
        verifyNoInteractions(comments);
    }

    @Test
    @DisplayName("공개 매물의 댓글은 상한 200건으로 읽는다")
    void list_readsActiveCommentsWithLimit() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        ListingComment comment = ListingComment.post("c-1", "listing-1", "buyer-1", "구매 가능한가요?", NOW);
        when(comments.findActiveByListingId("listing-1", 200)).thenReturn(List.of(comment));

        assertThat(service.list("listing-1")).containsExactly(comment);
    }

    @Test
    @DisplayName("숨김·삭제 매물에는 저장도 알림도 하지 않는다")
    void post_doesNotSaveOrNotifyWhenListingIsHidden() {
        when(listings.getPublicById("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));

        assertThatThrownBy(() -> service.post(command("deleted-listing", "buyer-1")))
                .isInstanceOf(ListingNotFoundException.class);
        verify(comments, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("세션 작성자로 저장하고 매물 판매자에게 알린다")
    void post_savesWithAuthorAndNotifiesSeller() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ListingComment saved = service.post(command("listing-1", "buyer-1"));

        assertThat(saved.authorId()).isEqualTo("buyer-1");
        assertThat(saved.listingId()).isEqualTo("listing-1");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.deleted()).isFalse();
        verify(sellerVerification).requireVerifiedSeller("seller-1");
        verify(notifier).notifySellerOfQuestion("seller-1", "listing-1", "에펠탑 10307");
    }

    @Test
    @DisplayName("판매자 본인 댓글이면 알리지 않는다")
    void post_doesNotNotifyWhenSellerCommentsOwnListing() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.post(command("listing-1", "seller-1"));

        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("판매자 신원확인이 준비되지 않았으면 저장 전에 거부한다")
    void post_failsClosedBeforeWriteWhenSellerIdentityIsNotReady() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        doThrow(new ServiceUnavailableException("SELLER_IDENTITY_VERIFICATION_UNAVAILABLE", "판매자 신원확인 준비 중"))
                .when(sellerVerification)
                .requireVerifiedSeller("seller-1");

        assertThatThrownBy(() -> service.post(command("listing-1", "buyer-1")))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(comments, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("알림 연계가 실패해도 저장한 댓글을 돌려준다")
    void post_keepsCommentWhenNotifierFails() {
        when(listings.getPublicById("listing-1")).thenReturn(listing("seller-1"));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("notification down"))
                .when(notifier)
                .notifySellerOfQuestion(anyString(), anyString(), anyString());

        assertThat(service.post(command("listing-1", "buyer-1")).content()).isEqualTo("구매 가능한가요?");
    }

    private static PostListingCommentCommand command(String listingId, String authorId) {
        return new PostListingCommentCommand(listingId, authorId, "구매 가능한가요?");
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
}
