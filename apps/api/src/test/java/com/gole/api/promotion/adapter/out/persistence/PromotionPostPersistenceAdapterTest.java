package com.gole.api.promotion.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.domain.model.CaptureDataSource;
import com.gole.api.promotion.domain.model.PromotionCapture;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionProvenance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

class PromotionPostPersistenceAdapterTest {

    private final PromotionPostMongoRepository repository = mock(PromotionPostMongoRepository.class);
    private final PromotionPostPersistenceAdapter adapter =
            new PromotionPostPersistenceAdapter(repository, mock(MongoTemplate.class));

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

    @Test
    @DisplayName("종류·설명표·출처가 저장했다 읽어도 그대로다")
    void contextSurvivesRoundTrip() {
        var capture = new PromotionCapture("필터 열린 목록", "/market", "필터 클릭", CaptureDataSource.DEMO, Instant.EPOCH);
        var polished = new PromotionCapture("검색", "/search", "", CaptureDataSource.DEMO, Instant.EPOCH)
                .withOriginal("/api/v1/media/images/raw.png", "목업에 넣음");
        var context = new PromotionPostContext(
                PromotionCategory.SERVICE,
                List.of(capture, polished),
                new PromotionProvenance("릴리스", "이유", "https://github.com/o/r/actions/runs/1"));
        PromotionPost post = PromotionPost.draft(
                "promo-1",
                PromotionChannel.THREADS,
                "캡션",
                List.of("/a.png", "/b.png"),
                "author-1",
                null,
                Instant.EPOCH,
                context);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.save(post);
        ArgumentCaptor<PromotionPostDocument> saved = ArgumentCaptor.forClass(PromotionPostDocument.class);
        verify(repository).save(saved.capture());
        when(repository.findById("promo-1")).thenReturn(Optional.of(saved.getValue()));
        PromotionPost read = adapter.findById("promo-1").orElseThrow();

        assertThat(read.context()).isEqualTo(context);
    }

    @Test
    @DisplayName("맥락 필드가 없는 예전 문서는 기능 홍보·설명표 없음으로 읽힌다")
    void legacyDocumentReadsAsFeature() {
        PromotionPost post = PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "캡션", List.of(), "author-1", null, Instant.EPOCH);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        adapter.save(post);
        ArgumentCaptor<PromotionPostDocument> saved = ArgumentCaptor.forClass(PromotionPostDocument.class);
        verify(repository).save(saved.capture());
        PromotionPostDocument legacy = saved.getValue();
        legacy.setContext(null, null, null);
        when(repository.findById("promo-1")).thenReturn(Optional.of(legacy));

        assertThat(adapter.findById("promo-1").orElseThrow().context()).isEqualTo(PromotionPostContext.NONE);
    }
}
