package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.admin.adapter.in.web.AdminExceptionQueueController.ResolveDisputeRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AdminExceptionQueueControllerValidationTest {

    @Test
    void disputeResolutionAcceptsOnlyKnownResolutionWithAuditNote() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new ResolveDisputeRequest("refund", "배송 사실 확인")))
                    .isEmpty();
            assertThat(validator.validate(new ResolveDisputeRequest("approve", "배송 사실 확인")))
                    .isNotEmpty();
            assertThat(validator.validate(new ResolveDisputeRequest("complete", "  ")))
                    .isNotEmpty();
        }
    }
}
