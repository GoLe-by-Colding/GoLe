package com.gole.api.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.notification.application.port.in.RegisterDeviceTokenUseCase;
import com.gole.api.notification.domain.model.DevicePlatform;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeviceTokenControllerTest {

    private FakeUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        useCase = new FakeUseCase();
        mvc = MockMvcBuilders.standaloneSetup(new DeviceTokenController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    @Test
    void register_storesTokenForSessionAccount() throws Exception {
        register("{\"token\":\"fcm-1\",\"platform\":\"IOS\"}").andExpect(status().isNoContent());

        assertThat(useCase.registered)
                .containsExactly(
                        new RegisterDeviceTokenUseCase.RegisterDeviceTokenCommand("me", "fcm-1", DevicePlatform.IOS));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"token\":\"fcm-1\"}",
                "{\"token\":\"fcm-1\",\"platform\":\"WINDOWS\"}",
                "{\"token\":\"fcm-1\",\"platform\":\"ios\"}",
                "{\"token\":\"\",\"platform\":\"IOS\"}",
                "{\"token\":\"   \",\"platform\":\"ANDROID\"}"
            })
    void register_rejectsMissingOrUnknownFieldsAsBadRequest(String body) throws Exception {
        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());

        assertThat(useCase.registered).isEmpty();
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/v1/notifications/devices")
                .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static final class FakeUseCase implements RegisterDeviceTokenUseCase {
        private final List<RegisterDeviceTokenCommand> registered = new ArrayList<>();

        @Override
        public void register(RegisterDeviceTokenCommand command) {
            registered.add(command);
        }

        @Override
        public void unregister(String token) {}
    }
}
