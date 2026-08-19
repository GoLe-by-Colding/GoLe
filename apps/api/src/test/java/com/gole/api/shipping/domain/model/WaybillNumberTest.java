package com.gole.api.shipping.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.shipping.domain.exception.InvalidWaybillException;
import org.junit.jupiter.api.Test;

class WaybillNumberTest {

    @Test
    void normalizesWhitespaceAndHyphens() {
        assertThat(new WaybillNumber(" 1234-5678-9012 ").value()).isEqualTo("123456789012");
        assertThat(new WaybillNumber("6889\t0123 4567").value()).isEqualTo("688901234567");
    }

    @Test
    void rejectsNonNumericCharacters() {
        assertThatThrownBy(() -> new WaybillNumber("12345678AB")).isInstanceOf(InvalidWaybillException.class);
    }

    @Test
    void rejectsOutOfRangeLength() {
        assertThatThrownBy(() -> new WaybillNumber("1234567")).isInstanceOf(InvalidWaybillException.class);
        assertThatThrownBy(() -> new WaybillNumber("1".repeat(21))).isInstanceOf(InvalidWaybillException.class);
        assertThatThrownBy(() -> new WaybillNumber("   ")).isInstanceOf(InvalidWaybillException.class);
    }
}
