package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.application.port.out.PromotionDraftRequestRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.domain.exception.DraftRequestLeaseLostException;
import com.gole.api.promotion.domain.exception.PromotionDraftRequestNotFoundException;
import com.gole.api.promotion.domain.exception.ReviewQueueFullException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.exception.SourceCommitRetryLimitExceededException;
import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import com.gole.api.promotion.domain.model.PromotionDraftRequestStatus;
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

class PromotionDraftRequestServiceTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";
    private static final String ADMIN = "admin-1";

    private final PromotionDraftRequestRepositoryPort requests = mock(PromotionDraftRequestRepositoryPort.class);
    private final PromotionPostRepositoryPort posts = mock(PromotionPostRepositoryPort.class);
    private final PromotionPostIdGeneratorPort idGenerator = mock(PromotionPostIdGeneratorPort.class);
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private final PromotionDraftRequestService service =
            new PromotionDraftRequestService(requests, posts, idGenerator, clock);

    private void reviewQueueHas(int pending) {
        when(posts.findRecentFirst(
                        PromotionPostStatus.PENDING_REVIEW, PromotionDraftRequestService.MAX_PENDING_REVIEW + 1))
                .thenReturn(java.util.Collections.nCopies(pending, mock(PromotionPost.class)));
    }

    private void noActiveRequests() {
        when(requests.findActiveBySourceCommitSha(anyString())).thenReturn(Optional.empty());
        when(requests.findActiveWithoutSourceCommit()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("커밋을 지정한 요청을 접수하면 PENDING으로 저장한다")
    void request_savesPendingForPinnedCommit() {
        noActiveRequests();
        reviewQueueHas(0);
        when(posts.existsBySourceCommitSha(SHA)).thenReturn(false);
        when(posts.countBySourceCommitSha(SHA)).thenReturn(0L);
        when(idGenerator.newId()).thenReturn("req-1");
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));

        PromotionDraftRequest accepted = service.request(SHA, ADMIN);

        assertThat(accepted.getStatus()).isEqualTo(PromotionDraftRequestStatus.PENDING);
        assertThat(accepted.getSourceCommitSha()).isEqualTo(SHA);
        assertThat(accepted.hasPinnedCommit()).isTrue();
        assertThat(accepted.getRequestedBy()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("커밋을 비우면 자동 선정 요청으로 저장한다")
    void request_allowsBlankCommitForAutomaticSelection() {
        noActiveRequests();
        reviewQueueHas(0);
        when(idGenerator.newId()).thenReturn("req-1");
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));

        PromotionDraftRequest accepted = service.request("  ", ADMIN);

        assertThat(accepted.hasPinnedCommit()).isFalse();
        assertThat(accepted.getSourceCommitSha()).isNull();
        // 자동 선정 요청에서는 커밋 기준 검사를 아예 하지 않는다.
        verify(posts, never()).existsBySourceCommitSha(anyString());
    }

    @Test
    @DisplayName("이미 점유 중인 릴리스는 접수 단계에서 사유와 함께 거절한다")
    void request_rejectsCommitStillClaimed() {
        noActiveRequests();
        when(posts.existsBySourceCommitSha(SHA)).thenReturn(true);

        assertThatThrownBy(() -> service.request(SHA, ADMIN)).isInstanceOf(SourceCommitAlreadyPromotedException.class);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("재시도 상한을 넘긴 릴리스는 접수하지 않는다")
    void request_rejectsCommitOverRetryLimit() {
        noActiveRequests();
        when(posts.existsBySourceCommitSha(SHA)).thenReturn(false);
        when(posts.countBySourceCommitSha(SHA)).thenReturn((long) PromotionPostService.MAX_DRAFTS_PER_SOURCE_COMMIT);

        assertThatThrownBy(() -> service.request(SHA, ADMIN))
                .isInstanceOf(SourceCommitRetryLimitExceededException.class);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("검토 대기가 상한이면 접수하지 않는다 — 에이전트도 어차피 조기 반환한다")
    void request_rejectsWhenReviewQueueIsFull() {
        noActiveRequests();
        when(posts.existsBySourceCommitSha(SHA)).thenReturn(false);
        when(posts.countBySourceCommitSha(SHA)).thenReturn(0L);
        reviewQueueHas(PromotionDraftRequestService.MAX_PENDING_REVIEW);

        assertThatThrownBy(() -> service.request(SHA, ADMIN)).isInstanceOf(ReviewQueueFullException.class);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("같은 커밋 요청이 이미 대기 중이면 새로 쌓지 않고 그것을 돌려준다")
    void request_isIdempotentWhileAnEarlierRequestIsActive() {
        PromotionDraftRequest existing = PromotionDraftRequest.pending("req-1", SHA, ADMIN, Instant.EPOCH);
        when(requests.findActiveBySourceCommitSha(SHA)).thenReturn(Optional.of(existing));

        assertThat(service.request(SHA, ADMIN)).isSameAs(existing);
        verify(requests, never()).save(any());
        // 멱등 반환이므로 홍보 가능 여부를 다시 묻지 않는다.
        verify(posts, never()).existsBySourceCommitSha(anyString());
    }

    @Test
    @DisplayName("자동 선정 요청도 대기 중이면 중복으로 쌓지 않는다")
    void request_isIdempotentForAutomaticSelectionToo() {
        PromotionDraftRequest existing = PromotionDraftRequest.pending("req-1", null, ADMIN, Instant.EPOCH);
        when(requests.findActiveWithoutSourceCommit()).thenReturn(Optional.of(existing));

        assertThat(service.request(null, ADMIN)).isSameAs(existing);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("점유는 저장소의 원자 갱신에 위임하며 실행 최악 소요보다 긴 lease를 넘긴다")
    void claimNext_delegatesWithLeaseLongerThanWorstCaseRun() {
        service.claimNext();

        ArgumentCaptor<java.time.Duration> lease = ArgumentCaptor.forClass(java.time.Duration.class);
        verify(requests).claimNext(any(), lease.capture(), anyInt());
        // 후보 3건 × 15분 = 45분보다 넉넉해야 정상 실행 중에 lease가 풀리지 않는다.
        assertThat(lease.getValue()).isGreaterThan(java.time.Duration.ofMinutes(45));
    }

    @Test
    @DisplayName("성공 회신은 초안 ID를 남기고 점유를 놓는다")
    void succeed_recordsPostIdAndReleasesLease() {
        PromotionDraftRequest claimed = PromotionDraftRequest.pending("req-1", SHA, ADMIN, Instant.EPOCH);
        claimed.claim("lease-1", Instant.EPOCH.plusSeconds(600));
        when(requests.findById("req-1")).thenReturn(Optional.of(claimed));
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));

        PromotionDraftRequest done = service.succeed("req-1", "lease-1", "post-9");

        assertThat(done.getStatus()).isEqualTo(PromotionDraftRequestStatus.SUCCEEDED);
        assertThat(done.getPromotionPostId()).isEqualTo("post-9");
        assertThat(done.getLeaseToken()).isNull();
        assertThat(done.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("실패 회신은 사유 코드만 남긴다")
    void fail_recordsOnlyTheCode() {
        PromotionDraftRequest claimed = PromotionDraftRequest.pending("req-1", SHA, ADMIN, Instant.EPOCH);
        claimed.claim("lease-1", Instant.EPOCH.plusSeconds(600));
        when(requests.findById("req-1")).thenReturn(Optional.of(claimed));
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));

        PromotionDraftRequest done = service.fail("req-1", "lease-1", "COMMIT_NOT_FOUND");

        assertThat(done.getStatus()).isEqualTo(PromotionDraftRequestStatus.FAILED);
        assertThat(done.getFailureCode()).isEqualTo("COMMIT_NOT_FOUND");
    }

    @Test
    @DisplayName("lease를 잃은 실행의 늦은 회신은 거절한다")
    void succeed_rejectsStaleLease() {
        PromotionDraftRequest claimed = PromotionDraftRequest.pending("req-1", SHA, ADMIN, Instant.EPOCH);
        claimed.claim("lease-2", Instant.EPOCH.plusSeconds(600));
        when(requests.findById("req-1")).thenReturn(Optional.of(claimed));

        assertThatThrownBy(() -> service.succeed("req-1", "lease-1", "post-9"))
                .isInstanceOf(DraftRequestLeaseLostException.class);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("없는 요청에 회신하면 404로 거절한다")
    void succeed_rejectsUnknownRequest() {
        when(requests.findById("req-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.succeed("req-x", "lease-1", "post-9"))
                .isInstanceOf(PromotionDraftRequestNotFoundException.class);
    }

    @Test
    @DisplayName("목록 조회는 저장소에 그대로 위임한다")
    void findRecentFirst_delegates() {
        when(requests.findRecentFirst(20)).thenReturn(List.of());

        assertThat(service.findRecentFirst(20)).isEmpty();
        verify(requests).findRecentFirst(20);
    }
}
