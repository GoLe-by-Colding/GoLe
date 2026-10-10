package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.admin.adapter.in.web.AdminPromotionMemoryController.*;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.domain.model.*;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminPromotionMemoryControllerTest {
    private final ManagePromotionMemoryUseCase memory = mock(ManagePromotionMemoryUseCase.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final AdminPromotionMemoryController controller = new AdminPromotionMemoryController(memory, audit);

    @Test
    @DisplayName("단건 반려 근거 조회는 ID로 유스케이스에 위임한다")
    void feedback_readsSourceById() {
        var source = mock(PromotionFeedback.class);
        when(memory.getFeedback("old-feedback")).thenReturn(source);

        assertThat(controller.feedback("old-feedback")).isSameAs(source);
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("성찰 제안은 인증한 제안자에게 귀속되며 임의 actor 입력을 받지 않는다")
    void reflect_usesAuthenticatedActor() {
        var http = new MockHttpServletRequest();
        http.setAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_ID, "agent");
        controller.reflect(new ReflectRequest(List.of("f1"), "run-1", List.of()), http);
        verify(memory)
                .reflect(eq("agent"), argThat(command -> command.feedbackIds().equals(List.of("f1"))));
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("중복 근거/적용범위와 같은 도메인 검증 실패는 400으로 변환한다")
    void invalidDomainInput_mapsToBadRequest() {
        when(memory.reflect(any(), any())).thenThrow(new IllegalArgumentException("duplicate feedback"));
        assertThatThrownBy(() -> controller.reflect(
                        new ReflectRequest(List.of("f1", "f1"), "run-1", List.of()), new MockHttpServletRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("성찰/편집 요청은 필수 필드, 예산, 빈 문자열과 null 원소를 검증한다")
    void requests_validateTrustBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new ReflectRequest(List.of("f1"), "run-1", List.of())))
                    .isEmpty();
            assertThat(validator.validate(new ReflectRequest(List.of(), "run-1", List.of())))
                    .isNotEmpty();
            assertThat(validator.validate(new ReflectRequest(List.of("f1"), "invalid key", List.of())))
                    .isNotEmpty();
            assertThat(validator.validate(new ReflectRequest(List.of("f1"), "run-1", null)))
                    .isNotEmpty();
            var proposal = new ProposalRequest(
                    PromotionGuidelineKind.PROCEDURE,
                    " ",
                    List.of(PromotionMemoryTarget.IMAGE_EDIT),
                    List.of(PromotionCategory.FEATURE),
                    List.of("f1"));
            assertThat(validator.validate(new ReflectRequest(List.of("f1"), "run-1", List.of(proposal))))
                    .isNotEmpty();
            assertThat(validator.validate(new EditRequest("수정", List.of(), List.of(PromotionCategory.FEATURE))))
                    .isNotEmpty();
        }
    }
}
