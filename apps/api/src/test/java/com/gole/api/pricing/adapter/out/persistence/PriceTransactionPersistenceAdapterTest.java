package com.gole.api.pricing.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class PriceTransactionPersistenceAdapterTest {

    @Test
    void platformSourceAndOrderReferenceRoundTrip() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        when(repository.save(any(PriceTransactionDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PriceTransactionPersistenceAdapter adapter =
                new PriceTransactionPersistenceAdapter(repository, mock(MongoTemplate.class));

        PriceTransaction saved = adapter.save(new PriceTransaction(
                "10307",
                850_000,
                1,
                Instant.parse("2026-08-30T00:00:00Z"),
                SetCondition.NEW_SEALED,
                PriceTransactionSource.PLATFORM_PAYMENT,
                "order-42"));

        ArgumentCaptor<PriceTransactionDocument> document = ArgumentCaptor.forClass(PriceTransactionDocument.class);
        verify(repository).save(document.capture());
        assertThat(document.getValue().getSource()).isEqualTo("platform_payment");
        assertThat(document.getValue().getSourceReference()).isEqualTo("order-42");
        assertThat(saved.source()).isEqualTo(PriceTransactionSource.PLATFORM_PAYMENT);
        assertThat(saved.sourceReference()).isEqualTo("order-42");
    }

    @Test
    void missingSourceIsPreservedAsUnverifiedLegacyEvidence() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        when(repository.findBySetNumberOrderByExecutedAtAsc("10307"))
                .thenReturn(List.of(new PriceTransactionDocument(
                        "legacy-1", "10307", 700_000, 1, Instant.parse("2025-01-01T00:00:00Z"), null)));
        PriceTransactionPersistenceAdapter adapter =
                new PriceTransactionPersistenceAdapter(repository, mock(MongoTemplate.class));

        List<PriceTransaction> result = adapter.findInRangeAscending("10307", null, null);

        assertThat(result)
                .singleElement()
                .extracting(PriceTransaction::source)
                .isEqualTo(PriceTransactionSource.LEGACY_UNVERIFIED);
    }

    @Test
    void sealedConditionQueryIncludesUntaggedLegacyDocuments() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(PriceTransactionDocument.class)))
                .thenReturn(List.of(new PriceTransactionDocument(
                        "legacy-1", "10307", 700_000, 1, Instant.parse("2025-01-01T00:00:00Z"), null)));
        PriceTransactionPersistenceAdapter adapter = new PriceTransactionPersistenceAdapter(repository, mongoTemplate);

        List<PriceTransaction> result = adapter.findByConditionAscending("10307", SetCondition.NEW_SEALED);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(PriceTransactionDocument.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"condition\": null");
        assertThat(result)
                .singleElement()
                .extracting(PriceTransaction::condition)
                .isEqualTo(SetCondition.NEW_SEALED);
    }
}
