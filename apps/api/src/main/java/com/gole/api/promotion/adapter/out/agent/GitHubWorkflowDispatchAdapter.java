package com.gole.api.promotion.adapter.out.agent;

import com.gole.api.promotion.application.port.out.PromotionAgentRunPort;
import com.gole.api.promotion.domain.exception.PromotionAgentNotConfiguredException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@code promotion-agent.yml} 을 {@code workflow_dispatch(mode=publish)} 로 시작시킨다.
 *
 * <p>토큰은 이 저장소의 Actions 쓰기 권한만 가진 fine-grained PAT 를 쓴다. 비어 있으면 호출하지 않고
 * 503 으로 알린다 — 설정 전 운영에서 버튼이 조용히 아무것도 안 하는 일을 막는다.
 */
@Component
public class GitHubWorkflowDispatchAdapter implements PromotionAgentRunPort {

    private final String token;
    private final String repository;
    private final String workflow;
    private final String ref;
    private final RestClient client;

    // 테스트용 생성자가 하나 더 있어 Spring 이 고를 생성자를 명시한다. 없으면 기동이 실패한다.
    @Autowired
    public GitHubWorkflowDispatchAdapter(
            @Value("${gole.promotion.agent.github-token:}") String token,
            @Value("${gole.promotion.agent.repository:GoLe-by-Colding/GoLe}") String repository,
            @Value("${gole.promotion.agent.workflow:promotion-agent.yml}") String workflow,
            @Value("${gole.promotion.agent.ref:main}") String ref) {
        this(token, repository, workflow, ref, RestClient.create("https://api.github.com"));
    }

    GitHubWorkflowDispatchAdapter(String token, String repository, String workflow, String ref, RestClient client) {
        this.token = token == null ? "" : token.trim();
        this.repository = repository;
        this.workflow = workflow;
        this.ref = ref;
        this.client = client;
    }

    @Override
    public void dispatchPublishRun(String requestedBy) {
        if (token.isEmpty()) {
            throw new PromotionAgentNotConfiguredException();
        }
        client.post()
                .uri("/repos/" + repository + "/actions/workflows/{workflow}/dispatches", workflow)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ref", ref, "inputs", Map.of("mode", "publish", "requested_by", requestedBy)))
                .retrieve()
                .toBodilessEntity();
    }
}
