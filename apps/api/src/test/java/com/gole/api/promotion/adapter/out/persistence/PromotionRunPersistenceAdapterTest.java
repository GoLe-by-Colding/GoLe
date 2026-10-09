package com.gole.api.promotion.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.domain.model.ModelCall;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class PromotionRunPersistenceAdapterTest {

    private final PromotionRunMongoRepository repository = mock(PromotionRunMongoRepository.class);
    private final PromotionRunPersistenceAdapter adapter = new PromotionRunPersistenceAdapter(repository);

    private static PromotionRun run(String id) {
        return new PromotionRun(
                id,
                "gh-9-1",
                PromotionCategory.FEATURE,
                "b".repeat(40),
                RunOutcome.SUBMITTED,
                RunReasonCode.SUBMITTED,
                null,
                "promo-1",
                "c".repeat(40),
                "https://github.com/GoLe-by-Colding/GoLe/actions/runs/9",
                List.of(new ModelCall("codex", "gpt-6.1-sol", false, 60, 50, 7, null, 70000L)),
                Instant.parse("2026-10-06T00:00:00Z"));
    }

    @Test
    @DisplayName("insert 로 남겨 같은 runKey 를 덮어쓰지 않고, 호출 사용량까지 그대로 되읽는다")
    void insertIfAbsent_roundTripsWithoutSave() {
        when(repository.insert(any(PromotionRunDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionRun stored = adapter.insertIfAbsent(run("run-1"));

        assertThat(stored).isEqualTo(run("run-1"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("unique 충돌이면 먼저 저장된 기록을 돌려준다")
    void insertIfAbsent_duplicate_returnsExisting() {
        when(repository.insert(any(PromotionRunDocument.class))).thenThrow(new DuplicateKeyException("dup"));
        PromotionRunDocument first = new PromotionRunDocument(
                "run-0", "gh-9-1", "FEATURE", null, "FAILED", "ERROR", null, null, null, null, null, Instant.EPOCH);
        when(repository.findByRunKey("gh-9-1")).thenReturn(Optional.of(first));

        PromotionRun stored = adapter.insertIfAbsent(run("run-1"));

        assertThat(stored.id()).isEqualTo("run-0");
        assertThat(stored.calls()).isEmpty();
    }
}
