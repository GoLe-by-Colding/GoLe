package com.gole.api.account.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService.ConsentStatus;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ThirdPartyProvisionConsentControllerTest {

    private ThirdPartyProvisionConsentService consents;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        consents = mock(ThirdPartyProvisionConsentService.class);
        GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
        when(sessions.resolve("session-token"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "member@example.test", Role.USER)));
        var controller = new ThirdPartyProvisionConsentController(consents, sessions, new SessionCookie("false"));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
                .build();
    }

    @Test
    void exposesCurrentStatusWithoutPersonalData() throws Exception {
        when(consents.currentStatus("account-1"))
                .thenReturn(new ConsentStatus("2026-09-04", true, Instant.parse("2026-09-04T01:02:03Z")));

        mvc.perform(get("/api/v1/accounts/me/third-party-provision-consents/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer session-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noticeVersion").value("2026-09-04"))
                .andExpect(jsonPath("$.consented").value(true))
                .andExpect(jsonPath("$.lastDecisionAt").value("2026-09-04T01:02:03Z"))
                .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    void recordsExplicitConsentPathAndRequestId() throws Exception {
        when(consents.consent("account-1", "2026-09-04", SourcePath.LISTING_CHAT, "request-0001"))
                .thenReturn(new ConsentStatus("2026-09-04", true, Instant.parse("2026-09-04T01:02:03Z")));

        mvc.perform(
                        post("/api/v1/accounts/me/third-party-provision-consents")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer session-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "noticeVersion": "2026-09-04",
                                  "accepted": true,
                                  "path": "LISTING_CHAT",
                                  "requestId": "request-0001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(true));

        verify(consents).consent("account-1", "2026-09-04", SourcePath.LISTING_CHAT, "request-0001");
    }

    @Test
    void uncheckedOrServerOnlySignupPathCannotCreateConsentEvidence() throws Exception {
        mvc.perform(
                        post("/api/v1/accounts/me/third-party-provision-consents")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer session-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "noticeVersion": "2026-09-04",
                                  "accepted": false,
                                  "path": "LISTING_CHAT",
                                  "requestId": "request-0001"
                                }
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/accounts/me/third-party-provision-consents")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer session-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "noticeVersion": "2026-09-04",
                                  "accepted": true,
                                  "path": "EMAIL_SIGNUP",
                                  "requestId": "request-0002"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_PATH_INVALID"));

        verify(consents, never()).consent(any(), any(), any(), any());
    }

    @Test
    void withdrawalIsASeparateAppendCommand() throws Exception {
        when(consents.withdraw("account-1", "2026-09-04", "request-0002"))
                .thenReturn(new ConsentStatus("2026-09-04", false, Instant.parse("2026-09-04T02:03:04Z")));

        mvc.perform(
                        post("/api/v1/accounts/me/third-party-provision-consent-withdrawals")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer session-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"noticeVersion":"2026-09-04","requestId":"request-0002"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consented").value(false));

        verify(consents).withdraw("account-1", "2026-09-04", "request-0002");
    }
}
