package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.AccountRequests.ChangePasswordRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.ConfirmPasswordResetRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.RequestPasswordResetRequest;
import com.gole.api.account.application.port.in.ChangePasswordUseCase;
import com.gole.api.account.application.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.port.in.PublicAuthRequestLimitUseCase;
import com.gole.api.account.application.port.in.RequestPasswordResetUseCase;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.common.web.ClientAddressResolver;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountPasswordControllerTest {

    private final ChangePasswordUseCase changePassword = mock(ChangePasswordUseCase.class);
    private final RequestPasswordResetUseCase requestReset = mock(RequestPasswordResetUseCase.class);
    private final ConfirmPasswordResetUseCase confirmReset = mock(ConfirmPasswordResetUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final SessionCookie sessionCookie = new SessionCookie("false");
    private final PublicAuthRequestLimitUseCase publicRequestLimit = mock(PublicAuthRequestLimitUseCase.class);
    private final AccountPasswordController controller = new AccountPasswordController(
            changePassword,
            requestReset,
            confirmReset,
            sessions,
            sessionCookie,
            publicRequestLimit,
            new ClientAddressResolver());

    @Test
    void changePasswordUsesCurrentSessionAndClearsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookie.NAME, "session-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessions.resolve("session-1"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)));

        controller.changePassword(new ChangePasswordRequest("old-password", "new-password"), request, response);

        ArgumentCaptor<ChangePasswordCommand> command = ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(changePassword).change(command.capture());
        assertThat(command.getValue().accountId()).isEqualTo("account-1");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("gole_session=", "Max-Age=0");
    }

    @Test
    void changePasswordRejectsMissingSessionBeforeUseCase() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.changePassword(
                        new ChangePasswordRequest("old-password", "new-password"),
                        request,
                        new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void publicResetEndpointsDelegateWithoutAccountDisclosure() {
        when(publicRequestLimit.acquirePasswordReset("member@gole.test", "127.0.0.1"))
                .thenReturn(true);
        controller.requestReset(new RequestPasswordResetRequest("member@gole.test"), new MockHttpServletRequest());
        controller.confirmReset(new ConfirmPasswordResetRequest("member@gole.test", "123456", "new-password"));

        verify(requestReset).request(any());
        verify(confirmReset).confirm(any());
    }

    @Test
    void passwordResetCooldownKeepsNoContentSemanticsWithoutCallingUseCase() {
        when(publicRequestLimit.acquirePasswordReset("member@gole.test", "127.0.0.1"))
                .thenReturn(false);

        controller.requestReset(new RequestPasswordResetRequest("member@gole.test"), new MockHttpServletRequest());

        org.mockito.Mockito.verifyNoInteractions(requestReset);
    }
}
