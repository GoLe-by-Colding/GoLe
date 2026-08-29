package com.gole.api.pricing.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.pricing.adapter.out.persistence.PriceTransactionMongoRepository;
import org.junit.jupiter.api.Test;

class PricingSeederTest {

    @Test
    void existingTransactionsAreNeverDeletedOrModified() {
        PriceTransactionMongoRepository repository = mock(PriceTransactionMongoRepository.class);
        when(repository.count()).thenReturn(1L);

        new PricingSeeder(repository).run();

        verify(repository).count();
        verifyNoMoreInteractions(repository);
    }
}
