package com.gole.api.pricing.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceTransactionSource;
import com.gole.api.pricing.domain.model.SetCondition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(PriceTransactionDocument.class)))
                .thenReturn(List.of(new PriceTransactionDocument(
                        "legacy-1", "10307", 700_000, 1, Instant.parse("2025-01-01T00:00:00Z"), null)));
        PriceTransactionPersistenceAdapter adapter = new PriceTransactionPersistenceAdapter(repository, mongoTemplate);

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

    @Test
    @DisplayName("시계열 조회는 같은 시각 체결도 같은 순서로 돌려준다")
    void timeSeriesQueriesBreakTiesByDocumentId() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(PriceTransactionDocument.class)))
                .thenReturn(List.of());
        PriceTransactionPersistenceAdapter adapter = new PriceTransactionPersistenceAdapter(repository, mongoTemplate);

        adapter.findInRangeAscending("10307", null, null);
        adapter.findInRangeAscending("10307", Instant.parse("2026-01-01T00:00:00Z"), null);
        adapter.findByConditionAscending("10307", SetCondition.NEW_SEALED);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(3)).find(query.capture(), eq(PriceTransactionDocument.class));
        // executedAt 만으로 정렬하면 MongoDB 가 동점의 순서를 보장하지 않아 차트 점 순서가
        // 호출마다 바뀐다. 세 경로 전부 _id 깨기값을 달고 나가야 한다.
        assertThat(query.getAllValues())
                .allSatisfy(captured ->
                        assertThat(captured.getSortObject().toJson()).isEqualTo("{\"executedAt\": 1, \"_id\": 1}"));
    }

    @Test
    @DisplayName("전체 기간 조회도 MongoTemplate 한 경로로 나간다")
    void fullRangeQueryGoesThroughTheSameReadPath() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(PriceTransactionDocument.class)))
                .thenReturn(List.of());
        PriceTransactionPersistenceAdapter adapter = new PriceTransactionPersistenceAdapter(repository, mongoTemplate);

        adapter.findInRangeAscending("10307", null, null);

        // 예전에는 이 경로만 파생 쿼리로 빠져 정렬 계약이 둘이었다.
        verifyNoInteractions(repository);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(PriceTransactionDocument.class));
        assertThat(query.getValue().getQueryObject().toJson()).doesNotContain("executedAt");
    }
}
