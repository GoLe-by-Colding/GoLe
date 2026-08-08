package com.gole.api.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

class LegoSetPersistenceAdapterTest {

    @Test
    void search_limitsBothQueriesAndEscapesSetNumberPrefix() {
        LegoSetMongoRepository repository = mock(LegoSetMongoRepository.class);
        when(repository.findByNameContainingIgnoreCaseOrThemeContainingIgnoreCase(eq(".*"), eq(".*"), any()))
                .thenReturn(List.of());
        when(repository.findBySetNumberStartingWith(any(), any())).thenReturn(List.of());
        LegoSetPersistenceAdapter adapter = new LegoSetPersistenceAdapter(repository);

        assertThat(adapter.searchByNameOrTheme(".*")).isEmpty();

        ArgumentCaptor<Limit> nameLimit = ArgumentCaptor.forClass(Limit.class);
        verify(repository)
                .findByNameContainingIgnoreCaseOrThemeContainingIgnoreCase(eq(".*"), eq(".*"), nameLimit.capture());
        assertThat(nameLimit.getValue().max()).isEqualTo(30);

        ArgumentCaptor<String> escapedPrefix = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Limit> prefixLimit = ArgumentCaptor.forClass(Limit.class);
        verify(repository).findBySetNumberStartingWith(escapedPrefix.capture(), prefixLimit.capture());
        assertThat(escapedPrefix.getValue()).isEqualTo(Pattern.quote(".*"));
        assertThat(prefixLimit.getValue().max()).isEqualTo(30);
    }
}
