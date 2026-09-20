package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaTargetType;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CreatePromotionPostCommand;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.application.port.out.SocialPublishPort;
import com.gole.api.promotion.application.port.out.SocialPublishPort.PublishResult;
import com.gole.api.promotion.domain.exception.InvalidPromotionPostStateException;
import com.gole.api.promotion.domain.exception.PromotionPostNotFoundException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PromotionPostServiceTest {

    private final PromotionPostRepositoryPort repository = mock(PromotionPostRepositoryPort.class);
    private final PromotionPostIdGeneratorPort idGenerator = mock(PromotionPostIdGeneratorPort.class);
    private final SocialPublishPort publishPort = mock(SocialPublishPort.class);
    private final ManageMediaAssetsUseCase mediaAssets = mock(ManageMediaAssetsUseCase.class);
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private final PromotionPostService service =
            new PromotionPostService(repository, idGenerator, publishPort, mediaAssets, clock);

    private PromotionPost saved(PromotionPostStatus status, String authorId) {
        PromotionPost post = PromotionPost.draft(
                "promo-1", PromotionChannel.THREADS, "캡션", List.of(), authorId, null, Instant.EPOCH);
        if (status != PromotionPostStatus.DRAFT) {
            post.submitForReview(Instant.EPOCH);
        }
        if (status == PromotionPostStatus.APPROVED || status == PromotionPostStatus.PUBLISHED) {
            post.approve("reviewer-1", Instant.EPOCH);
        }
        if (status == PromotionPostStatus.PUBLISHED) {
            post.markPublished("threads-1", Instant.EPOCH);
        }
        return post;
    }

    @Test
    void createSavesDraftAndReturnsId() {
        when(idGenerator.newId()).thenReturn("promo-1");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String sourceCommitSha = "0123456789abcdef0123456789abcdef01234567";

        String id = service.create(new CreatePromotionPostCommand(
                "author-1", PromotionChannel.THREADS, "새 기능 나왔습니다", List.of(), sourceCommitSha));

        assertThat(id).isEqualTo("promo-1");
        ArgumentCaptor<PromotionPost> captor = ArgumentCaptor.forClass(PromotionPost.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PromotionPostStatus.DRAFT);
        assertThat(captor.getValue().getAuthorId()).isEqualTo("author-1");
        assertThat(captor.getValue().getSourceCommitSha()).isEqualTo(sourceCommitSha);
    }

    @Test
    void createAttachesMediaKeysAndStoresPublicPaths() {
        when(idGenerator.newId()).thenReturn("promo-1");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String key = "images/11111111-1111-4111-8111-111111111111.png";

        String id = service.create(
                new CreatePromotionPostCommand("author-1", PromotionChannel.THREADS, "새 기능 나왔습니다", List.of(key), null));

        assertThat(id).isEqualTo("promo-1");
        verify(mediaAssets)
                .replaceReferences("author-1", MediaTargetType.PROMOTION_POST, "promo-1", List.of(key), true);
        ArgumentCaptor<PromotionPost> captor = ArgumentCaptor.forClass(PromotionPost.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMediaUrls()).containsExactly("/api/v1/media/" + key);
    }

    @Test
    @DisplayName("같은 릴리스 SHA로는 초안을 두 번 만들 수 없다 — 미디어도 건드리지 않는다")
    void createRejectsDuplicateSourceCommitSha() {
        String sourceCommitSha = "0123456789abcdef0123456789abcdef01234567";
        when(repository.existsBySourceCommitSha(sourceCommitSha)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreatePromotionPostCommand(
                        "author-1", PromotionChannel.THREADS, "새 기능 나왔습니다", List.of(), sourceCommitSha)))
                .isInstanceOf(SourceCommitAlreadyPromotedException.class);

        verify(repository, never()).save(any());
        // STAGED 이미지를 PUBLIC으로 전이시키기 전에 막아야 고아 이미지가 남지 않는다(D8).
        verify(mediaAssets, never()).replaceReferences(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("sourceCommitSha가 없는 초안은 중복 검사를 거치지 않는다")
    void createSkipsDuplicateCheckWhenSourceCommitShaIsNull() {
        when(idGenerator.newId()).thenReturn("promo-1");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreatePromotionPostCommand("author-1", PromotionChannel.THREADS, "캡션", List.of(), null));

        verify(repository, never()).existsBySourceCommitSha(any());
        verify(repository).save(any());
    }

    @Test
    void publishChecksApprovedBeforeCallingPublishPort() {
        PromotionPost draft = saved(PromotionPostStatus.DRAFT, "author-1");
        when(repository.findById("promo-1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.publish("promo-1")).isInstanceOf(InvalidPromotionPostStateException.class);

        verify(publishPort, never()).publish(any());
    }

    @Test
    void publishCallsPublishPortAndStoresExternalPostId() {
        PromotionPost approved = saved(PromotionPostStatus.APPROVED, "author-1");
        when(repository.findById("promo-1")).thenReturn(Optional.of(approved));
        when(publishPort.publish(approved)).thenReturn(new PublishResult("stub-post-1"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionPost result = service.publish("promo-1");

        assertThat(result.getStatus()).isEqualTo(PromotionPostStatus.PUBLISHED);
        assertThat(result.getExternalPostId()).isEqualTo("stub-post-1");
    }

    @Test
    void approveRejectsSelfReview() {
        PromotionPost pending = saved(PromotionPostStatus.PENDING_REVIEW, "author-1");
        when(repository.findById("promo-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve("promo-1", "author-1"))
                .isInstanceOf(com.gole.api.promotion.domain.exception.SelfReviewNotAllowedException.class);
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(PromotionPostNotFoundException.class);
    }
}
