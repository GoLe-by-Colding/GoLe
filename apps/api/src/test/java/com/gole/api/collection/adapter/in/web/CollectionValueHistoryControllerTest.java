package com.gole.api.collection.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.collection.application.port.in.GetCollectionValueHistoryUseCase;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 자산 추이 조회 HTTP 계약. (collection-value-history H3) */
class CollectionValueHistoryControllerTest {

    private MockMvc mvc;
    private GetCollectionValueHistoryUseCase history;

    @BeforeEach
    void setUp() {
        history = mock(GetCollectionValueHistoryUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new CollectionController(null, null, history))
                .setControllerAdvice(new GlobalExceptionHandler(event -> {}))
                .build();
    }

    @Test
    @DisplayName("경로의 userId가 아니라 세션 계정의 추이를 기본 90일로 돌려준다")
    void valueHistory_usesSessionAccountAndDefaultDays() throws Exception {
        Instant captured = Instant.parse("2026-03-11T00:00:00Z");
        when(history.history("me", 90))
                .thenReturn(List.of(
                        new CollectionValueSnapshot("me", LocalDate.of(2026, 3, 10), 250_000, 2, 1, captured),
                        new CollectionValueSnapshot("me", LocalDate.of(2026, 3, 11), 280_000, 2, 1, captured)));

        mvc.perform(get("/api/v1/collections/someone-else/value-history")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.points[1].ownedValue").value(280_000))
                .andExpect(jsonPath("$.points[1].ownedCount").value(2))
                .andExpect(jsonPath("$.points[1].pricedCount").value(1));
        verify(history).history("me", 90);
    }

    @Test
    @DisplayName("기간 검증 실패는 400 INVALID_PARAMETER로 나간다")
    void valueHistory_mapsInvalidDaysTo400() throws Exception {
        when(history.history(anyString(), anyInt()))
                .thenThrow(new BadRequestException("INVALID_PARAMETER", "조회 기간(days)은 1~365일이어야 합니다"));

        mvc.perform(get("/api/v1/collections/me/value-history")
                        .param("days", "400")
                        .requestAttr(UserAuthInterceptor.ATTR_ACCOUNT_ID, "me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
