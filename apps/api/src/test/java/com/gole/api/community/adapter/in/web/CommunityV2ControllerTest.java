package com.gole.api.community.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.community.adapter.in.web.CommunityV2Dtos.PatchPostRequest;
import com.gole.api.community.application.port.in.PatchPostUseCase;
import com.gole.api.community.application.port.in.PatchPostUseCase.PatchPostCommand;
import com.gole.api.community.domain.exception.PostContentRequiredException;
import com.gole.api.community.domain.model.Post;
import com.gole.api.community.domain.model.PostStatus;
import com.gole.api.community.domain.model.PostType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class CommunityV2ControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jackson_distinguishesOmittedBodyFromExplicitEmptyBody() throws Exception {
        PatchPostRequest omitted = objectMapper.readValue("{\"status\":\"draft\"}", PatchPostRequest.class);
        PatchPostRequest empty = objectMapper.readValue("{\"body\":\"\",\"status\":\"draft\"}", PatchPostRequest.class);

        assertThat(omitted.bodyProvided()).isFalse();
        assertThat(empty.bodyProvided()).isTrue();
        assertThat(empty.body()).isEmpty();
    }

    @Test
    void patch_mapsDocumentedPhotoOnlyDraftRequestWithoutLosingEmptyBody() throws Exception {
        PatchPostUseCase useCase = mock(PatchPostUseCase.class);
        Post saved = new Post(
                "post-1",
                "author-1",
                "",
                List.of("community/example.svg"),
                PostType.GENERAL,
                PostStatus.DRAFT,
                Set.of(),
                Instant.parse("2026-08-09T00:00:00Z"));
        when(useCase.patch(any())).thenReturn(saved);
        CommunityV2Controller controller = new CommunityV2Controller(useCase);
        PatchPostRequest request = objectMapper.readValue(
                """
                {
                  "body": "",
                  "mediaKeys": ["images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg"],
                  "visibility": "public",
                  "status": "draft"
                }
                """,
                PatchPostRequest.class);
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, "author-1");

        var response = controller.patch("post-1", request, http);

        ArgumentCaptor<PatchPostCommand> command = ArgumentCaptor.forClass(PatchPostCommand.class);
        verify(useCase).patch(command.capture());
        assertThat(command.getValue().body().provided()).isTrue();
        assertThat(command.getValue().body().value()).isEmpty();
        assertThat(command.getValue().photos().value())
                .containsExactly("images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg");
        assertThat(command.getValue().status().value()).isEqualTo(PostStatus.DRAFT);
        assertThat(response.body()).isEmpty();
        assertThat(response.mediaKeys()).isEmpty();
        assertThat(response.imageUrls()).containsExactly("/api/v1/media/community/example.svg");
        assertThat(response.status()).isEqualTo("draft");
    }

    @Test
    void patch_publishedWithEmptyBody_returnsBadRequest() throws Exception {
        PatchPostUseCase useCase = mock(PatchPostUseCase.class);
        when(useCase.patch(any())).thenThrow(new PostContentRequiredException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CommunityV2Controller(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();

        mockMvc.perform(patch("/api/v2/community/posts/post-1")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "author-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\",\"status\":\"published\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_CONTENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("발행 게시글은 본문이 필요합니다"));
    }
}
