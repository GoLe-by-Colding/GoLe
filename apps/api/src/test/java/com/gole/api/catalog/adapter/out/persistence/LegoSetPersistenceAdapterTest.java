package com.gole.api.catalog.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.catalog.domain.model.RetirementStatus;
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

    @Test
    void themeWords_matchAcrossSpacingAndCaseAndReturnSetNumbers() {
        LegoSetMongoRepository repository = mock(LegoSetMongoRepository.class);
        when(repository.findByThemeRegex(any(), any()))
                .thenReturn(List.of(
                        new LegoSetDocument(
                                "75192", "밀레니엄 팰컨", "Star Wars", 7541, 2017, RetirementStatus.ACTIVE, null, false),
                        new LegoSetDocument(
                                "75313", "AT-AT", "Star Wars", 6785, 2021, RetirementStatus.ACTIVE, null, false)));
        LegoSetPersistenceAdapter adapter = new LegoSetPersistenceAdapter(repository);

        assertThat(adapter.loadSetNumbersByThemeWords(List.of("star", "wars"), 300))
                .containsExactly("75192", "75313");

        ArgumentCaptor<Pattern> theme = ArgumentCaptor.forClass(Pattern.class);
        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(repository).findByThemeRegex(theme.capture(), limit.capture());
        // 낱말은 글자 그대로(quote), 사이의 띄어쓰기·하이픈·밑줄은 가리지 않고, 대소문자도 가리지 않는다.
        assertThat(theme.getValue().pattern()).isEqualTo(Pattern.quote("star") + "[\\s_-]*" + Pattern.quote("wars"));
        assertThat(theme.getValue().flags() & Pattern.CASE_INSENSITIVE).isEqualTo(Pattern.CASE_INSENSITIVE);
        assertThat(theme.getValue().matcher("Star Wars").find()).isTrue();
        assertThat(theme.getValue().matcher("star-wars").find()).isTrue();
        assertThat(theme.getValue().matcher("Marvel Star Wars Saga").find()).isTrue();
        assertThat(theme.getValue().matcher("Stars").find()).isFalse();
        assertThat(limit.getValue().max()).isEqualTo(300);
    }
}
