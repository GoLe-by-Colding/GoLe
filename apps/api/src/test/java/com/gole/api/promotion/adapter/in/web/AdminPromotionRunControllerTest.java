package com.gole.api.promotion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.promotion.adapter.in.web.AdminPromotionRunController.CallRequest;
import com.gole.api.promotion.adapter.in.web.AdminPromotionRunController.RecordRunRequest;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordedRun;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AdminPromotionRunControllerTest {

    private final RecordPromotionRunUseCase useCase = mock(RecordPromotionRunUseCase.class);
    private final AdminPromotionRunController controller = new AdminPromotionRunController(useCase);

    private static final CallRequest CALL = new CallRequest("codex", "gpt-6.1-sol", true, 60, 50, 7, null, 70000L);

    private static RecordRunRequest request(
            String runKey, RunOutcome outcome, RunReasonCode reason, List<CallRequest> calls) {
        return new RecordRunRequest(
                runKey, PromotionCategory.SERVICE, null, outcome, reason, "볼 게 없음", null, null, null, calls);
    }

    private static PromotionRun stored() {
        return new PromotionRun(
                "run-1",
                "gh-1-1",
                PromotionCategory.SERVICE,
                null,
                RunOutcome.SKIPPED,
                RunReasonCode.MODEL_SKIPPED,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.EPOCH);
    }

    @Test
    @DisplayName("새로 남기면 201, 같은 runKey 재전송이면 200")
    void record_statusTellsWhetherItWasNew() {
        when(useCase.record(any())).thenReturn(new RecordedRun(stored(), true), new RecordedRun(stored(), false));
        RecordRunRequest request = request("gh-1-1", RunOutcome.SKIPPED, RunReasonCode.MODEL_SKIPPED, List.of(CALL));

        assertThat(controller.record(request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.record(request).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("도메인이 거부한 조합은 500 이 아니라 400 으로 돌려준다")
    void record_domainRejection_isBadRequest() {
        when(useCase.record(any())).thenThrow(new IllegalArgumentException("a submitted run must name its draft"));

        assertThatThrownBy(() ->
                        controller.record(request("gh-1-1", RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED, List.of())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("요청 검증: runKey 형식·호출 수 상한·음수 토큰·GitHub 밖 실행 링크를 거부한다")
    void request_validation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(
                            request("gh-1-1", RunOutcome.SKIPPED, RunReasonCode.MODEL_SKIPPED, List.of(CALL))))
                    .isEmpty();
            assertThat(validator.validate(request("GH 1", RunOutcome.SKIPPED, RunReasonCode.MODEL_SKIPPED, List.of())))
                    .isNotEmpty();
            assertThat(validator.validate(request(
                            "gh-1-1",
                            RunOutcome.SKIPPED,
                            RunReasonCode.MODEL_SKIPPED,
                            Collections.nCopies(PromotionRun.MAX_CALLS + 1, CALL))))
                    .isNotEmpty();
            assertThat(validator.validate(new CallRequest("codex", null, true, -1, 0, 0, null, null)))
                    .isNotEmpty();
            assertThat(validator.validate(new RecordRunRequest(
                            "gh-1-1",
                            PromotionCategory.SERVICE,
                            null,
                            RunOutcome.SKIPPED,
                            RunReasonCode.MODEL_SKIPPED,
                            null,
                            null,
                            null,
                            "https://evil.example/x",
                            List.of())))
                    .isNotEmpty();
        }
    }
}
