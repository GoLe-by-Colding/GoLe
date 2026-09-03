package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.LogoutUseCase;
import com.gole.api.account.application.port.in.RefreshSessionUseCase;
import com.gole.api.account.application.port.in.RefreshSessionUseCase.RefreshSessionResult;
import com.gole.api.account.application.port.in.RegisterAccountUseCase;
import com.gole.api.account.application.port.in.RegisterAccountUseCase.RegisterAccountCommand;
import com.gole.api.account.application.port.in.ResendVerificationUseCase;
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.domain.model.Role;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {

    private RefreshSessionUseCase refreshSessions;
    private RegisterAccountUseCase registerAccounts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        refreshSessions = mock(RefreshSessionUseCase.class);
        registerAccounts = mock(RegisterAccountUseCase.class);
        var controller = new AccountController(
                registerAccounts,
                mock(ResendVerificationUseCase.class),
                mock(VerifyEmailUseCase.class),
                mock(SignInUseCase.class),
                mock(GetCurrentSessionUseCase.class),
                mock(LogoutUseCase.class),
                refreshSessions,
                new SessionCookie("false", Duration.ofDays(7)));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void registerMapsExplicitPolicyAcceptanceToUseCase() throws Exception {
        when(registerAccounts.register(org.mockito.ArgumentMatchers.any())).thenReturn("account-1");

        mvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "email": "member@gole.test",
                                  "password": "password1",
                                  "termsVersion": "2026-09-03",
                                  "privacyVersion": "2026-09-03",
                                  "termsAccepted": true,
                                  "privacyAcknowledged": true,
                                  "minimumAgeConfirmed": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("account-1"));

        ArgumentCaptor<RegisterAccountCommand> command = ArgumentCaptor.forClass(RegisterAccountCommand.class);
        verify(registerAccounts).register(command.capture());
        assertThat(command.getValue().policyAcceptance().termsVersion()).isEqualTo("2026-09-03");
        assertThat(command.getValue().policyAcceptance().privacyAcknowledged()).isTrue();
        assertThat(command.getValue().policyAcceptance().minimumAgeConfirmed()).isTrue();
    }

    @Test
    void registerRejectsUncheckedRequiredPolicyBeforeUseCase() throws Exception {
        mvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "email": "member@gole.test",
                                  "password": "password1",
                                  "termsVersion": "2026-09-03",
                                  "privacyVersion": "2026-09-03",
                                  "termsAccepted": false,
                                  "privacyAcknowledged": true,
                                  "minimumAgeConfirmed": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(registerAccounts);
    }

    @Test
    void cookieRefreshRotatesHttpOnlyCookieWithoutExposingTokenInJson() throws Exception {
        when(refreshSessions.refresh("old-cookie"))
                .thenReturn(Optional.of(
                        new RefreshSessionResult("account-1", "new-cookie", Role.USER, true, Duration.ofDays(6))));

        mvc.perform(post("/api/v1/accounts/sessions/refresh").cookie(new Cookie(SessionCookie.NAME, "old-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("account-1"))
                .andExpect(jsonPath("$.sessionToken").value(""))
                .andExpect(jsonPath("$.rotated").value(true))
                .andExpect(header().string(
                                HttpHeaders.SET_COOKIE,
                                org.hamcrest.Matchers.containsString("gole_session=new-cookie")));
    }

    @Test
    void bearerRefreshReturnsReplacementWithoutSettingBrowserCookie() throws Exception {
        when(refreshSessions.refresh("old-bearer"))
                .thenReturn(Optional.of(
                        new RefreshSessionResult("account-1", "new-bearer", Role.ADMIN, true, Duration.ofDays(6))));

        mvc.perform(post("/api/v1/accounts/sessions/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer old-bearer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("new-bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
