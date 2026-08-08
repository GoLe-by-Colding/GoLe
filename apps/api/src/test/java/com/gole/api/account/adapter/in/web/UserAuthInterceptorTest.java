package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UserAuthInterceptorTest {

    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final UserAuthInterceptor interceptor = new UserAuthInterceptor(sessions);

    @Test
    void publicGetDoesNotRequireSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/listings");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
    }

    @Test
    void writeRequiresValidSessionAndSetsActor() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        request.addHeader("Authorization", "Bearer session-1");
        when(sessions.resolve("session-1"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)));

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID)).isEqualTo("account-1");
    }

    @Test
    void missingSessionRejectsWrite() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void orderReadRequiresSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/order-1");
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }
}
