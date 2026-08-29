package com.gole.api.pricing.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.pricing.adapter.out.persistence.PriceTransactionDocument;
import com.gole.api.pricing.adapter.out.persistence.PriceTransactionMongoRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PricingSeederTest {

    @Test
    void existingTransactionsAreNeverDeletedOrModified() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        when(repository.count()).thenReturn(1L);

        new PricingSeeder(repository).run();

        verify(repository).count();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyCollectionReceivesOnlyExplicitDemoEvidence() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        when(repository.count()).thenReturn(0L);

        new PricingSeeder(repository).run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PriceTransactionDocument>> documents = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(documents.capture());
        List<PriceTransactionDocument> saved = new java.util.ArrayList<>();
        documents.getValue().forEach(saved::add);
        assertThat(saved).hasSize(792).allMatch(document -> "demo_seed".equals(document.getSource()));
    }
}
