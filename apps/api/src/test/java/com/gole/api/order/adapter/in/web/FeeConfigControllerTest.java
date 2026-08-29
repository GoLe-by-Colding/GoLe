package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.order.domain.model.FeePolicy;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class FeeConfigControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("공개 수수료 응답은 실제 정책의 요율과 하한·상한을 반환한다")
    void exposesConfiguredFeePolicy() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FeeConfigController(new FeePolicy(0.035, 500, 50_000)))
                .build();

        mvc.perform(get("/api/v1/config/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.035))
                .andExpect(jsonPath("$.minFee").value(500))
                .andExpect(jsonPath("$.maxFee").value(50_000));
    }

    @Test
    @DisplayName("공개 응답은 안전한 수수료 필드 세 개만 노출한다")
    void exposesOnlySafePublicFields() {
        var response = new FeeConfigController(new FeePolicy(0.05, 0, 0)).fees();
        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(response), new TypeReference<Map<String, Object>>() {});

        assertThat(json).containsOnlyKeys("rate", "minFee", "maxFee");
    }
}
