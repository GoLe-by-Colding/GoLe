package com.gole.api.promotion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.ManageAccountsUseCase;
import com.gole.api.account.application.port.in.ManageAccountsUseCase.AccountSummary;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.UnauthorizedException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PromotionAgentTokenInterceptorTest {

    private static final String TOKEN = "promotion-token-for-test";
    private static final String HASH = HexFormat.of().formatHex(PromotionAgentTokenInterceptor.sha256(TOKEN));
    private static final String BOT = "bot@gole.test";

    private final ManageAccountsUseCase accounts = mock(ManageAccountsUseCase.class);

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/internal/promotion-agent/deliveries");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private void bot(Role role, AccountStatus status) {
        when(accounts.list(anyString(), anyInt()))
                .thenReturn(List.of(new AccountSummary("bot-1", BOT, role, status, null, null)));
    }

    private boolean handle(PromotionAgentTokenInterceptor interceptor, MockHttpServletRequest request) {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("맞는 토큰이면 봇 계정 id 를 요청에 싣고 통과시킨다")
    void validToken_actsAsBot() {
        bot(Role.ADMIN, AccountStatus.VERIFIED);
        MockHttpServletRequest request = request("Bearer " + TOKEN);

        assertThat(handle(new PromotionAgentTokenInterceptor(HASH, BOT, accounts), request))
                .isTrue();
        assertThat(request.getAttribute(PromotionAgentTokenInterceptor.ATTR_ACTOR_ID))
                .isEqualTo("bot-1");
    }

    @Test
    @DisplayName("토큰이 없거나 다르면 401 — 해시를 그대로 보내도 통하지 않는다")
    void wrongToken_isUnauthorized() {
        bot(Role.ADMIN, AccountStatus.VERIFIED);
        var interceptor = new PromotionAgentTokenInterceptor(HASH, BOT, accounts);

        for (String header : new String[] {null, "Bearer ", "Bearer wrong", "Bearer " + HASH, TOKEN}) {
            assertThatThrownBy(() -> handle(interceptor, request(header)))
                    .as(String.valueOf(header))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    @DisplayName("설정이 비었거나 형식이 틀리면 창구가 닫힌다(fail-closed)")
    void notConfigured_isForbidden() {
        bot(Role.ADMIN, AccountStatus.VERIFIED);

        for (String hash : new String[] {"", "abc", HASH.substring(1)}) {
            assertThatThrownBy(() ->
                            handle(new PromotionAgentTokenInterceptor(hash, BOT, accounts), request("Bearer " + TOKEN)))
                    .isInstanceOf(ForbiddenException.class);
        }
        assertThatThrownBy(() ->
                        handle(new PromotionAgentTokenInterceptor(HASH, "", accounts), request("Bearer " + TOKEN)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("봇 계정이 ADMIN 이 아니거나 정지됐거나 없으면 막는다")
    void unusableBot_isForbidden() {
        var interceptor = new PromotionAgentTokenInterceptor(HASH, BOT, accounts);

        bot(Role.USER, AccountStatus.VERIFIED);
        assertThatThrownBy(() -> handle(interceptor, request("Bearer " + TOKEN)))
                .isInstanceOf(ForbiddenException.class);
        bot(Role.ADMIN, AccountStatus.SUSPENDED);
        assertThatThrownBy(() -> handle(interceptor, request("Bearer " + TOKEN)))
                .isInstanceOf(ForbiddenException.class);
        when(accounts.list(anyString(), anyInt()))
                .thenReturn(List.of(
                        new AccountSummary("x", "other-" + BOT, Role.ADMIN, AccountStatus.VERIFIED, null, null)));
        assertThatThrownBy(() -> handle(interceptor, request("Bearer " + TOKEN)))
                .isInstanceOf(ForbiddenException.class);
    }
}
