package com.gole.api.order.adapter.out.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.order.domain.model.PaymentEvidenceKind;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase;
import com.gole.api.pricing.application.port.in.RecordExecutedPriceUseCase.RecordExecutedPriceCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExecutedPriceRecorderAdapterTest {

    private static final Instant EXECUTED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void livePortOneOrderBecomesFirstPartyEvidence() {
        RecordExecutedPriceUseCase recorder = mock(RecordExecutedPriceUseCase.class);
        ExecutedPriceRecorderAdapter adapter = new ExecutedPriceRecorderAdapter(recorder);

        adapter.record("order-live", "10307", 850_000, 1, EXECUTED_AT, "new_sealed", PaymentEvidenceKind.LIVE);

        ArgumentCaptor<RecordExecutedPriceCommand> command = ArgumentCaptor.forClass(RecordExecutedPriceCommand.class);
        verify(recorder).record(command.capture());
        assertThat(command.getValue().source()).isEqualTo("platform_payment");
        assertThat(command.getValue().sourceReference()).isEqualTo("order-live");
    }

    @Test
    void testAndUnverifiedOrdersNeverBecomeFirstPartyEvidence() {
        RecordExecutedPriceUseCase testRecorder = mock(RecordExecutedPriceUseCase.class);
        new ExecutedPriceRecorderAdapter(testRecorder)
                .record("order-test", "10307", 850_000, 1, EXECUTED_AT, "new_sealed", PaymentEvidenceKind.TEST);
        ArgumentCaptor<RecordExecutedPriceCommand> testCommand =
                ArgumentCaptor.forClass(RecordExecutedPriceCommand.class);
        verify(testRecorder).record(testCommand.capture());
        assertThat(testCommand.getValue().source()).isEqualTo("platform_test");

        RecordExecutedPriceUseCase legacyRecorder = mock(RecordExecutedPriceUseCase.class);
        new ExecutedPriceRecorderAdapter(legacyRecorder)
                .record("order-legacy", "10307", 850_000, 1, EXECUTED_AT, "new_sealed", null);
        ArgumentCaptor<RecordExecutedPriceCommand> legacyCommand =
                ArgumentCaptor.forClass(RecordExecutedPriceCommand.class);
        verify(legacyRecorder).record(legacyCommand.capture());
        assertThat(legacyCommand.getValue().source()).isEqualTo("legacy_unverified");
    }
}
