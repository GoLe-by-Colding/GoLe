package com.gole.api.listing.adapter.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.catalog.application.port.in.FindThemeSetNumbersUseCase;
import com.gole.api.listing.domain.model.InterestTag;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogThemeSetsAdapterTest {

    private final FindThemeSetNumbersUseCase catalog = mock(FindThemeSetNumbersUseCase.class);
    private final CatalogThemeSetsAdapter adapter = new CatalogThemeSetsAdapter(catalog);

    @Test
    void asksCatalogWithTheInterestTagKeyAndALimit() {
        when(catalog.setNumbersInTheme("star-wars", CatalogThemeSetsAdapter.MAX_THEME_SETS))
                .thenReturn(List.of("75192", "75313"));

        assertThat(adapter.setNumbersInTheme(InterestTag.STAR_WARS)).containsExactly("75192", "75313");
        verify(catalog).setNumbersInTheme("star-wars", 300);
    }

    @Test
    void catalogFailureLeavesSearchWithTagAndTextOnly() {
        when(catalog.setNumbersInTheme(anyString(), anyInt())).thenThrow(new IllegalStateException("mongo down"));

        assertThat(adapter.setNumbersInTheme(InterestTag.ICONS)).isEmpty();
    }
}
