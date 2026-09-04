package com.gole.api.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.Decision;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ThirdPartyProvisionConsentPersistenceAdapterTest {

    @Test
    void appendsWithInsertAndNeverUsesMutableSave() {
        ThirdPartyProvisionConsentMongoRepository repository = mock(ThirdPartyProvisionConsentMongoRepository.class);
        when(repository.insert(any(ThirdPartyProvisionConsentDocument.class))).thenAnswer(call -> call.getArgument(0));
        var adapter = new ThirdPartyProvisionConsentPersistenceAdapter(repository);

        ThirdPartyProvisionConsentEvent stored = adapter.appendOnce(event());

        assertThat(stored).isEqualTo(event());
        verify(repository).insert(any(ThirdPartyProvisionConsentDocument.class));
        verify(repository, never()).save(any(ThirdPartyProvisionConsentDocument.class));
    }

    @Test
    void duplicateAccountRequestReturnsOriginalEvidenceWithoutOverwritingIt() {
        ThirdPartyProvisionConsentMongoRepository repository = mock(ThirdPartyProvisionConsentMongoRepository.class);
        when(repository.insert(any(ThirdPartyProvisionConsentDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        ThirdPartyProvisionConsentDocument original = document(event());
        when(repository.findByAccountIdAndRequestId("account-1", "request-0001"))
                .thenReturn(Optional.of(original));
        var adapter = new ThirdPartyProvisionConsentPersistenceAdapter(repository);

        ThirdPartyProvisionConsentEvent stored = adapter.appendOnce(event());

        assertThat(stored.occurredAt()).isEqualTo(Instant.parse("2026-09-04T01:02:03Z"));
        assertThat(stored.decision()).isEqualTo(Decision.CONSENTED);
        verify(repository, never()).save(any(ThirdPartyProvisionConsentDocument.class));
    }

    private static ThirdPartyProvisionConsentEvent event() {
        return new ThirdPartyProvisionConsentEvent(
                "event-1",
                "account-1",
                "2026-09-04",
                Decision.CONSENTED,
                SourcePath.LISTING_CHAT,
                "request-0001",
                Instant.parse("2026-09-04T01:02:03Z"));
    }

    private static ThirdPartyProvisionConsentDocument document(ThirdPartyProvisionConsentEvent event) {
        return new ThirdPartyProvisionConsentDocument(
                event.id(),
                event.accountId(),
                event.noticeVersion(),
                event.decision().name(),
                event.sourcePath().name(),
                event.requestId(),
                event.occurredAt());
    }
}
