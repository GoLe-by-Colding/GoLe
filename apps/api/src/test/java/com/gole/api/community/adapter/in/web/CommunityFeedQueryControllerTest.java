package com.gole.api.community.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.community.application.port.in.CommentOnPostUseCase;
import com.gole.api.community.application.port.in.DeleteCommentUseCase;
import com.gole.api.community.application.port.in.DeletePostUseCase;
import com.gole.api.community.application.port.in.EditPostUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase;
import com.gole.api.community.application.port.in.GetFeedUseCase.FeedPage;
import com.gole.api.community.application.port.in.LikePostUseCase;
import com.gole.api.community.application.port.in.PublishPostUseCase;
import com.gole.api.community.application.port.in.ReportCommentUseCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommunityFeedQueryControllerTest {

    private GetFeedUseCase feed;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        feed = mock(GetFeedUseCase.class);
        when(feed.feedPage(anyInt(), any(), any(), any())).thenReturn(new FeedPage(List.of(), false));
        mvc = MockMvcBuilders.standaloneSetup(new CommunityController(
                        mock(PublishPostUseCase.class),
                        mock(CommentOnPostUseCase.class),
                        mock(LikePostUseCase.class),
                        feed,
                        mock(DeletePostUseCase.class),
                        mock(DeleteCommentUseCase.class),
                        mock(EditPostUseCase.class),
                        mock(ReportCommentUseCase.class)))
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();
    }

    @Test
    void feedPage_searchesControlCharactersAsSpacesInsteadOfFailing() throws Exception {
        mvc.perform(get("/api/v1/community/posts/page").param("q", "레고\n테크닉\u0000세트"))
                .andExpect(status().isOk());

        verify(feed).feedPage(anyInt(), any(), any(), eq(Optional.of("레고 테크닉 세트")));
    }

    @Test
    void feedPage_treatsControlCharactersOnlyQueryAsNoQuery() throws Exception {
        mvc.perform(get("/api/v1/community/posts/page").param("q", "\u0000\u001F"))
                .andExpect(status().isOk());

        verify(feed).feedPage(anyInt(), any(), any(), eq(Optional.empty()));
    }

    @Test
    void feedPage_passesStrippedQueryToUseCase() throws Exception {
        mvc.perform(get("/api/v1/community/posts/page").param("q", "  에펠탑 \t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(feed).feedPage(anyInt(), any(), any(), eq(Optional.of("에펠탑")));
    }
}
