package com.gole.api.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ConditionDisclosureTest {

    @Test
    void missingPartsRequireAConcreteDescription() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConditionDisclosure(Completeness.BULK, false, false, true, null, ""))
                .withMessage("누락 부품이 있으면 상세 설명이 필요합니다");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConditionDisclosure(Completeness.BULK, false, false, true, "   ", ""))
                .withMessage("누락 부품이 있으면 상세 설명이 필요합니다");
    }

    @Test
    void disclosureNotesAllowExactlyOneThousandCharacters() {
        String boundary = "가".repeat(1000);

        ConditionDisclosure disclosure =
                new ConditionDisclosure(Completeness.NO_BOX, false, true, true, boundary, boundary);

        assertThat(disclosure.missingPartsNote()).hasSize(1000);
        assertThat(disclosure.defectsNote()).hasSize(1000);
    }

    @Test
    void eitherDisclosureNoteRejectsMoreThanOneThousandCharacters() {
        String overLimit = "가".repeat(1001);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConditionDisclosure(Completeness.NO_BOX, false, true, false, overLimit, ""))
                .withMessage("고지 설명은 1000자를 넘을 수 없습니다");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConditionDisclosure(Completeness.NO_BOX, false, true, false, "", overLimit))
                .withMessage("고지 설명은 1000자를 넘을 수 없습니다");
    }

    @Test
    void notesAreNormalizedBeforeValidationAndStorage() {
        ConditionDisclosure disclosure =
                new ConditionDisclosure(Completeness.FULL_BOX, true, true, true, "  헤드라이트 1개  ", null);

        assertThat(disclosure.missingPartsNote()).isEqualTo("헤드라이트 1개");
        assertThat(disclosure.defectsNote()).isEmpty();
    }

    @Test
    void completenessIsRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ConditionDisclosure(null, false, false, false, "", ""))
                .withMessage("completeness");
    }

    @Test
    void basicDisclosureIsAConservativeLegacyFallback() {
        ConditionDisclosure disclosure = ConditionDisclosure.basic();

        assertThat(disclosure.completeness()).isEqualTo(Completeness.NO_BOX);
        assertThat(disclosure.hasBox()).isFalse();
        assertThat(disclosure.hasManual()).isFalse();
        assertThat(disclosure.hasMissingParts()).isFalse();
        assertThat(disclosure.missingPartsNote()).isEmpty();
        assertThat(disclosure.defectsNote()).isEmpty();
    }
}
