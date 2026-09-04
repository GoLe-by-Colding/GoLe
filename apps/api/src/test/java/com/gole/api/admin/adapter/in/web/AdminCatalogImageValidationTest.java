package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.admin.adapter.in.web.AdminDtos.CreateSetRequest;
import com.gole.api.catalog.domain.model.RetirementStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AdminCatalogImageValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestAcceptsOnlyEmptyOrBundledCatalogPath() {
        CreateSetRequest external = request("https://tracker.example/image.jpg");
        CreateSetRequest userMedia = request("/api/v1/media/images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg");

        assertThat(validator.validate(external))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("imageUrl");
        assertThat(validator.validate(userMedia))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("imageUrl");
        assertThat(validator.validate(request("/api/v1/media/catalog/10307.svg")))
                .isEmpty();
        assertThat(validator.validate(request(""))).isEmpty();
    }

    private static CreateSetRequest request(String imageUrl) {
        return new CreateSetRequest(
                "10307", "Eiffel Tower", "Icons", 10_001, 2022, RetirementStatus.ACTIVE, imageUrl, false);
    }
}
