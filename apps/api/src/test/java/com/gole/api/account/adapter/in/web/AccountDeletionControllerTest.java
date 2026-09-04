package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.AccountDeletionController.DeletionRequest;
import com.gole.api.account.application.concurrency.AccountMutationGate;
import com.gole.api.account.application.concurrency.AccountMutationGate.Lease;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase.Command;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase.Result;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountDeletionControllerTest {

    private final RequestAccountDeletionUseCase deletions = mock(RequestAccountDeletionUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final SessionCookie cookie = new SessionCookie("false");
    private final AccountMutationGate gate = new AccountMutationGate();
    private final AccountDeletionController controller =
            new AccountDeletionController(deletions, sessions, cookie, gate);

    @Test
    void authenticatedRequestUsesServerSessionIdentityAndClearsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookie.NAME, "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sessions.resolve("session-token"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)));
        when(deletions.request(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Result("request-1", AccountDeletionStatus.READY, List.of(), Instant.EPOCH));

        var result = controller.request(
                new DeletionRequest("member@gole.test", "회원 탈퇴", "123456"),
                "550e8400-e29b-41d4-a716-446655440000",
                request,
                response);

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        verify(deletions).request(command.capture());
        verify(sessions, times(2)).resolve("session-token");
        assertThat(command.getValue().accountId()).isEqualTo("account-1");
        assertThat(result.requestId()).isEqualTo("request-1");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("gole_session=", "Max-Age=0");
    }

    @Test
    void sessionIsRevalidatedAfterExclusiveLeaseAndPrincipalChangeFailsClosed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookie.NAME, "session-token"));
        when(sessions.resolve("session-token"))
                .thenReturn(
                        Optional.of(new CurrentSession("account-1", "member@gole.test", Role.USER)),
                        Optional.of(new CurrentSession("account-2", "other@gole.test", Role.USER)));

        assertThatThrownBy(() -> controller.request(
                        new DeletionRequest("member@gole.test", "회원 탈퇴", "123456"),
                        "550e8400-e29b-41d4-a716-446655440000",
                        request,
                        new MockHttpServletResponse()))
                .isInstanceOf(UnauthorizedException.class);

        verify(deletions, never()).request(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @Timeout(5)
    void deletionWaitsForExistingAccountMutationBeforeSuspending() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionCookie.NAME, "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CurrentSession session = new CurrentSession("account-1", "member@gole.test", Role.USER);
        CountDownLatch initialSessionResolved = new CountDownLatch(1);
        when(sessions.resolve("session-token")).thenAnswer(ignored -> {
            initialSessionResolved.countDown();
            return Optional.of(session);
        });
        when(deletions.request(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Result("request-1", AccountDeletionStatus.READY, List.of(), Instant.EPOCH));
        Lease inFlightMutation = gate.acquireShared("account-1");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<AccountDeletionController.DeletionResponse> result = worker.submit(() -> controller.request(
                    new DeletionRequest("member@gole.test", "회원 탈퇴", "123456"),
                    "550e8400-e29b-41d4-a716-446655440000",
                    request,
                    response));
            initialSessionResolved.await();

            assertThat(result.isDone()).isFalse();
            verify(deletions, never()).request(org.mockito.ArgumentMatchers.any());
            inFlightMutation.close();

            assertThat(result.get().requestId()).isEqualTo("request-1");
            verify(sessions, times(2)).resolve("session-token");
        } finally {
            inFlightMutation.close();
            worker.close();
        }
    }
}
