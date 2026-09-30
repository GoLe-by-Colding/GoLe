package com.gole.api.promotion.adapter.out.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class GitHubWorkflowDispatchAdapterContextTest {

    @Test
    @DisplayName("Spring 이 토큰 없이도 어댑터 빈을 만든다 — 생성자 선택 실패로 API 기동이 막히던 회귀")
    void springCreatesAdapterBean() {
        try (var context = new AnnotationConfigApplicationContext(GitHubWorkflowDispatchAdapter.class)) {
            assertThat(context.getBean(GitHubWorkflowDispatchAdapter.class)).isNotNull();
        }
    }
}
