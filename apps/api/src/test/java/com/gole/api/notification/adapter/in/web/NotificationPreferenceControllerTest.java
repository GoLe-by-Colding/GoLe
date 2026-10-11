package com.gole.api.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.notification.application.port.in.ManageNotificationPreferencesUseCase;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationPreferenceControllerTest {

    private FakeUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        useCase = new FakeUseCase();
        mvc = MockMvcBuilders.standaloneSetup(new NotificationPreferenceController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    @Test
    void get_returnsCategoriesInEnumOrderForSessionAccount() throws Exception {
        useCase.stored.put(
                "me",
                NotificationPreferences.defaults("me")
                        .update(Map.of(NotificationCategory.COMMUNITY, false), Instant.EPOCH));

        mvc.perform(get("/api/v1/users/someone-else/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(4))
                .andExpect(jsonPath("$.categories[0].key").value("trade"))
                .andExpect(jsonPath("$.categories[0].label").value("거래 진행"))
                .andExpect(jsonPath("$.categories[0].enabled").value(true))
                .andExpect(jsonPath("$.categories[0].mandatory").value(true))
                .andExpect(jsonPath("$.categories[1].key").value("offer"))
                .andExpect(jsonPath("$.categories[2].key").value("watch"))
                .andExpect(jsonPath("$.categories[3].key").value("community"))
                .andExpect(jsonPath("$.categories[3].enabled").value(false))
                .andExpect(jsonPath("$.categories[3].mandatory").value(false));
    }

    @Test
    void put_updatesOnlySessionAccountAndReturnsSameShape() throws Exception {
        mvc.perform(put("/api/v1/users/someone-else/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": {\"offer\": false, \"trade\": true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[1].key").value("offer"))
                .andExpect(jsonPath("$.categories[1].enabled").value(false))
                .andExpect(jsonPath("$.categories[0].enabled").value(true));

        assertThat(useCase.stored).containsOnlyKeys("me");
    }

    @Test
    void put_unknownKeyIs400() throws Exception {
        mvc.perform(put("/api/v1/users/me/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": {\"marketing\": false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_CATEGORY_UNKNOWN"));

        assertThat(useCase.stored).isEmpty();
    }

    @Test
    void put_enumNameInsteadOfKeyIs400() throws Exception {
        mvc.perform(put("/api/v1/users/me/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": {\"OFFER\": false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_CATEGORY_UNKNOWN"));
    }

    @Test
    void put_turningOffTradeIs400() throws Exception {
        mvc.perform(put("/api/v1/users/me/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": {\"trade\": false, \"offer\": false}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_CATEGORY_MANDATORY"));

        assertThat(useCase.stored).isEmpty();
    }

    @Test
    void put_missingEnabledOrNullValueIs400() throws Exception {
        mvc.perform(put("/api/v1/users/me/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/users/me/notification-preferences")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": {\"offer\": null}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** 도메인 규칙(필수 분류 거부)을 그대로 타도록 실제 도메인 객체로 동작하는 가짜. */
    private static final class FakeUseCase implements ManageNotificationPreferencesUseCase {

        private final Map<String, NotificationPreferences> stored = new HashMap<>();

        @Override
        public NotificationPreferences get(String accountId) {
            return stored.getOrDefault(accountId, NotificationPreferences.defaults(accountId));
        }

        @Override
        public NotificationPreferences update(String accountId, Map<NotificationCategory, Boolean> changes) {
            NotificationPreferences next = get(accountId).update(changes, Instant.EPOCH);
            stored.put(accountId, next);
            return next;
        }
    }
}
