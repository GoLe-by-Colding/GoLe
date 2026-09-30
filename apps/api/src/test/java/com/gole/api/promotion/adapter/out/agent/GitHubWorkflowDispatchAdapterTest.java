package com.gole.api.promotion.adapter.out.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import com.gole.api.promotion.domain.exception.PromotionAgentNotConfiguredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubWorkflowDispatchAdapterTest {

    @Test
    @DisplayName("토큰이 없으면 호출하지 않고 503 으로 알린다")
    void dispatch_rejectsWithoutToken() {
        var adapter = new GitHubWorkflowDispatchAdapter(" ", "o/r", "w.yml", "main", RestClient.create());

        assertThatThrownBy(() -> adapter.dispatchPublishRun("admin"))
                .isInstanceOf(PromotionAgentNotConfiguredException.class);
    }

    @Test
    @DisplayName("publish 모드로 워크플로를 dispatch 한다")
    void dispatch_postsPublishModeInput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.github.com/repos/o/r/actions/workflows/w.yml/dispatches"))
                .andExpect(header("Authorization", "Bearer t"))
                .andExpect(jsonPath("$.ref").value("main"))
                .andExpect(jsonPath("$.inputs.mode").value("publish"))
                .andExpect(jsonPath("$.inputs.requested_by").value("admin"))
                .andRespond(withNoContent());
        var adapter = new GitHubWorkflowDispatchAdapter("t", "o/r", "w.yml", "main", builder.build());

        adapter.dispatchPublishRun("admin");

        server.verify();
    }
}
