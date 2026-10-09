package com.gole.api.listing.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.listing.application.port.in.ListListingCommentsUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase;
import com.gole.api.listing.application.port.in.PostListingCommentUseCase.PostListingCommentCommand;
import com.gole.api.listing.domain.exception.ListingNotFoundException;
import com.gole.api.listing.domain.model.ListingComment;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ListingCommentControllerTest {

    private final ListListingCommentsUseCase listComments = mock(ListListingCommentsUseCase.class);
    private final PostListingCommentUseCase postComment = mock(PostListingCommentUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ListingCommentController(listComments, postComment))
            .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
            .build();

    @Test
    @DisplayName("숨김 매물의 댓글 조회·작성은 HTTP 경계에서 404 다")
    void hiddenListingComments_returnNotFoundAtHttpBoundary() throws Exception {
        when(listComments.list("deleted-listing")).thenThrow(new ListingNotFoundException("deleted-listing"));
        when(postComment.post(any())).thenThrow(new ListingNotFoundException("deleted-listing"));

        mvc.perform(get("/api/v1/listings/deleted-listing/comments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mvc.perform(post("/api/v1/listings/deleted-listing/comments")
                        .requestAttr(AuthenticatedUser.ATTRIBUTE, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"구매 가능한가요?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("작성자는 세션 계정이고 본문의 authorId 는 무시한다")
    void create_usesAuthenticatedAuthorNotRequestBody() throws Exception {
        when(postComment.post(any()))
                .thenReturn(ListingComment.post(
                        "c-1", "listing-1", "buyer-1", "구매 가능한가요?", Instant.parse("2026-10-10T00:00:00Z")));

        mvc.perform(post("/api/v1/listings/listing-1/comments")
                        .requestAttr(AuthenticatedUser.ATTRIBUTE, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorId\":\"forged-author\",\"content\":\"구매 가능한가요?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value("buyer-1"));

        ArgumentCaptor<PostListingCommentCommand> command = ArgumentCaptor.forClass(PostListingCommentCommand.class);
        verify(postComment).post(command.capture());
        assertThat(command.getValue()).isEqualTo(new PostListingCommentCommand("listing-1", "buyer-1", "구매 가능한가요?"));
    }

    @Test
    @DisplayName("빈 본문은 유스케이스까지 가지 않고 400 이다")
    void create_rejectsBlankContent() throws Exception {
        mvc.perform(post("/api/v1/listings/listing-1/comments")
                        .requestAttr(AuthenticatedUser.ATTRIBUTE, "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(postComment, never()).post(any());
    }
}
