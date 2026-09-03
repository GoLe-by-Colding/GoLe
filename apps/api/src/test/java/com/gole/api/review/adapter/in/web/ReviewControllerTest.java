package com.gole.api.review.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.review.application.port.in.GetSellerReviewsUseCase;
import com.gole.api.review.application.port.in.ReplyToReviewUseCase;
import com.gole.api.review.application.port.in.ReplyToReviewUseCase.ReplyToReviewCommand;
import com.gole.api.review.application.port.in.WriteReviewUseCase;
import com.gole.api.review.domain.model.Review;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class ReviewControllerTest {

    @Test
    void reply_usesAuthenticatedAccountAndReturnsPublicReply() {
        ReplyToReviewUseCase replies = mock(ReplyToReviewUseCase.class);
        Review replied = new Review(
                "review-1",
                "order-1",
                "buyer-1",
                "seller-1",
                5,
                "좋은 거래였어요",
                Instant.parse("2026-09-03T00:00:00Z"),
                "거래 고맙습니다",
                Instant.parse("2026-09-03T01:00:00Z"),
                null,
                null);
        when(replies.reply(any())).thenReturn(replied);
        ReviewController controller =
                new ReviewController(mock(WriteReviewUseCase.class), mock(GetSellerReviewsUseCase.class), replies);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, "seller-1");

        var response = controller.reply("review-1", new ReviewDtos.ReplyReviewRequest("거래 고맙습니다"), http);

        ArgumentCaptor<ReplyToReviewCommand> command = ArgumentCaptor.forClass(ReplyToReviewCommand.class);
        verify(replies).reply(command.capture());
        assertThat(command.getValue().reviewId()).isEqualTo("review-1");
        assertThat(command.getValue().sellerId()).isEqualTo("seller-1");
        assertThat(command.getValue().content()).isEqualTo("거래 고맙습니다");
        assertThat(response.reply()).isEqualTo("거래 고맙습니다");
        assertThat(response.repliedAt()).isEqualTo(Instant.parse("2026-09-03T01:00:00Z"));
    }
}
