package com.gole.api.promotion.adapter.out.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionPostPersistenceAdapterTest {

    private final PromotionPostMongoRepository repository = mock(PromotionPostMongoRepository.class);
    private final PromotionPostPersistenceAdapter adapter = new PromotionPostPersistenceAdapter(repository);

    @Test
    @DisplayName("발행 목록은 발행 시각 순으로 자른다 — 최신 발행이 limit 밖으로 밀리지 않게")
    void findRecentFirst_ordersPublishedByPublishedAt() {
        when(repository.findByStatusOrderByPublishedAtDesc(eq("PUBLISHED"), any()))
                .thenReturn(List.of());

        adapter.findRecentFirst(PromotionPostStatus.PUBLISHED, 20);

        verify(repository).findByStatusOrderByPublishedAtDesc(eq("PUBLISHED"), any());
        verify(repository, never()).findByStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("다른 상태 목록은 그대로 생성 순이다")
    void findRecentFirst_keepsCreatedAtForOtherStatuses() {
        when(repository.findByStatusOrderByCreatedAtDesc(eq("APPROVED"), any())).thenReturn(List.of());

        adapter.findRecentFirst(PromotionPostStatus.APPROVED, 20);

        verify(repository).findByStatusOrderByCreatedAtDesc(eq("APPROVED"), any());
    }
}
