package com.gole.api.pricing.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.domain.model.MarketDataState;
import com.gole.api.pricing.domain.model.PriceSnapshot;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PricingControllerTest {

    @Test
    void snapshotExposesReferenceStageAndEvidenceProvenance() throws Exception {
        GetPriceInsightsUseCase useCase = mock(GetPriceInsightsUseCase.class);
        when(useCase.getSnapshot("10307"))
                .thenReturn(new PriceSnapshot(
                        "10307",
                        MarketDataState.OBSERVATIONS_ONLY,
                        3,
                        1,
                        List.of(new PriceTransaction(
                                "10307",
                                850_000,
                                1,
                                Instant.parse("2026-08-30T00:00:00Z"),
                                SetCondition.NEW_SEALED,
                                PriceTransactionSource.PLATFORM_PAYMENT,
                                "order-42")),
                        null,
                        null,
                        Set.of(PriceTransactionSource.PLATFORM_PAYMENT),
                        false));
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new PricingController(useCase)).build();

        mvc.perform(get("/api/v1/pricing/sets/10307/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OBSERVATIONS_ONLY"))
                .andExpect(jsonPath("$.minimumSamples").value(3))
                .andExpect(jsonPath("$.sampleCount").value(1))
                .andExpect(jsonPath("$.statistics").doesNotExist())
                .andExpect(jsonPath("$.observations[0].price").value(850_000))
                .andExpect(jsonPath("$.observations[0].source").value("platform_payment"))
                .andExpect(jsonPath("$.observations[0].condition").value("new_sealed"))
                .andExpect(jsonPath("$.provenance.mode").value("FIRST_PARTY"))
                .andExpect(jsonPath("$.provenance.demo").value(false));
    }

    @Test
    void emptySnapshotDoesNotClaimFirstPartyEvidence() throws Exception {
        GetPriceInsightsUseCase useCase = mock(GetPriceInsightsUseCase.class);
        when(useCase.getSnapshot("99999"))
                .thenReturn(new PriceSnapshot(
                        "99999", MarketDataState.EMPTY, 3, 0, List.of(), null, null, Set.of(), false));
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new PricingController(useCase)).build();

        mvc.perform(get("/api/v1/pricing/sets/99999/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMPTY"))
                .andExpect(jsonPath("$.provenance.mode").value("NONE"));
    }

    @Test
    void unverifiedLegacyEvidenceIsNeverLabelledFirstParty() throws Exception {
        GetPriceInsightsUseCase useCase = mock(GetPriceInsightsUseCase.class);
        when(useCase.getSnapshot("10307"))
                .thenReturn(new PriceSnapshot(
                        "10307",
                        MarketDataState.OBSERVATIONS_ONLY,
                        3,
                        1,
                        List.of(new PriceTransaction(
                                "10307", 700_000, 1, Instant.parse("2025-01-01T00:00:00Z"), SetCondition.NEW_SEALED)),
                        null,
                        null,
                        Set.of(PriceTransactionSource.LEGACY_UNVERIFIED),
                        false));
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new PricingController(useCase)).build();

        mvc.perform(get("/api/v1/pricing/sets/10307/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance.mode").value("LEGACY_UNVERIFIED"));
    }

    @Test
    void testChannelPaymentIsLabelledAsDemoEvidence() throws Exception {
        GetPriceInsightsUseCase useCase = mock(GetPriceInsightsUseCase.class);
        when(useCase.getSnapshot("10307"))
                .thenReturn(new PriceSnapshot(
                        "10307",
                        MarketDataState.OBSERVATIONS_ONLY,
                        3,
                        1,
                        List.of(new PriceTransaction(
                                "10307",
                                700_000,
                                1,
                                Instant.parse("2026-08-30T00:00:00Z"),
                                SetCondition.NEW_SEALED,
                                PriceTransactionSource.PLATFORM_TEST,
                                "test-order")),
                        null,
                        null,
                        Set.of(PriceTransactionSource.PLATFORM_TEST),
                        true));
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup(new PricingController(useCase)).build();

        mvc.perform(get("/api/v1/pricing/sets/10307/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[0].source").value("platform_test"))
                .andExpect(jsonPath("$.provenance.mode").value("DEMO"))
                .andExpect(jsonPath("$.provenance.demo").value(true));
    }
}
