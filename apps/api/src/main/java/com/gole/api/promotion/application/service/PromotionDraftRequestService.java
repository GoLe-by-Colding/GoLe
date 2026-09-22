package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.ConsumePromotionDraftRequestUseCase;
import com.gole.api.promotion.application.port.in.RequestPromotionDraftUseCase;
import com.gole.api.promotion.application.port.out.PromotionDraftRequestRepositoryPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.domain.exception.PromotionDraftRequestNotFoundException;
import com.gole.api.promotion.domain.exception.ReviewQueueFullException;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.exception.SourceCommitRetryLimitExceededException;
import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 콘솔 발 홍보 초안 요청의 접수와 소비(promotion-review D20).
 *
 * <p><b>이 서비스는 홍보 가능 여부의 규칙을 새로 만들지 않는다.</b> 점유와 3회 상한은
 * {@link PromotionPostService} 가 이미 갖고 있고 최종 방어선은 문서의 unique 인덱스다. 여기서는
 * 그 규칙을 <b>접수 시점에 미리 물어볼 뿐</b>이다 — 같은 규칙을 두 벌 쓰면 반드시 어긋난다.
 *
 * <p>미리 묻는 이유는 둘이다. 유료 모델 호출을 낭비하기 전에 막는 것, 그리고 더 중요하게는
 * <b>거절 사유를 관리자에게 보이는 것</b>이다. 지금은 Python 스캐너가 이미 홍보한 커밋에서
 * 조용히 멈춰(`hands.py`) 후보 0건으로 정상 종료하므로, 관리자 눈에는 아무 일도 일어나지
 * 않은 것처럼 보인다.
 */
@Service
public class PromotionDraftRequestService implements RequestPromotionDraftUseCase, ConsumePromotionDraftRequestUseCase {

    /**
     * 검토 대기 상한 — 넘으면 새 요청을 받지 않는다.
     *
     * <p>에이전트의 {@code policy.MAX_PENDING_REVIEW} 와 같은 값이어야 한다. 사람이 소화하지
     * 못하는 속도로 초안을 쌓으면 검토 품질이 떨어진다(D18).
     */
    static final int MAX_PENDING_REVIEW = 5;

    /** 한 요청이 집히는 최대 횟수. 넘으면 FAILED 로 닫아 무한 재점유를 막는다. */
    static final int MAX_ATTEMPTS = 3;

    /**
     * 점유 유지 시간.
     *
     * <p>에이전트 한 실행의 최악 소요(후보 3 × 15분 = 45분)보다 넉넉해야 한다 — 짧으면
     * 정상 실행 중에 lease 가 풀려 다른 실행이 같은 요청을 집는다.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(90);

    private final PromotionDraftRequestRepositoryPort requests;
    private final PromotionPostRepositoryPort posts;
    private final PromotionPostIdGeneratorPort idGenerator;
    private final Clock clock;

    public PromotionDraftRequestService(
            PromotionDraftRequestRepositoryPort requests,
            PromotionPostRepositoryPort posts,
            PromotionPostIdGeneratorPort idGenerator,
            Clock clock) {
        this.requests = requests;
        this.posts = posts;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PromotionDraftRequest request(String sourceCommitSha, String requestedBy) {
        String sha = (sourceCommitSha == null || sourceCommitSha.isBlank())
                ? null
                : sourceCommitSha.trim().toLowerCase(java.util.Locale.ROOT);

        // 같은 요청을 두 번 쌓지 않는다. 버튼 연타·새로고침이 요청을 늘리면 에이전트가 같은
        // 일을 여러 번 하게 된다.
        Optional<PromotionDraftRequest> active =
                sha == null ? requests.findActiveWithoutSourceCommit() : requests.findActiveBySourceCommitSha(sha);
        if (active.isPresent()) {
            return active.get();
        }

        if (sha != null) {
            // 점유 기준이다 — 반려된 초안은 점유를 놓아줬으므로 여기서 걸리지 않고, 그 릴리스는
            // 관리자가 다시 요청할 수 있다(D2).
            if (posts.existsBySourceCommitSha(sha)) {
                throw new SourceCommitAlreadyPromotedException(sha);
            }
            if (posts.countBySourceCommitSha(sha) >= PromotionPostService.MAX_DRAFTS_PER_SOURCE_COMMIT) {
                throw new SourceCommitRetryLimitExceededException(
                        sha, PromotionPostService.MAX_DRAFTS_PER_SOURCE_COMMIT);
            }
        }

        // 검토가 밀려 있으면 에이전트도 어차피 조기 반환한다(runtime.py). 그 사실을 여기서
        // 알려주지 않으면 요청만 쌓이고 아무 일도 안 일어난다.
        if (posts.findRecentFirst(PromotionPostStatus.PENDING_REVIEW, MAX_PENDING_REVIEW + 1)
                        .size()
                >= MAX_PENDING_REVIEW) {
            throw new ReviewQueueFullException(MAX_PENDING_REVIEW);
        }

        Instant now = clock.instant();
        return requests.save(PromotionDraftRequest.pending(idGenerator.newId(), sha, requestedBy, now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionDraftRequest> findRecentFirst(int limit) {
        return requests.findRecentFirst(limit);
    }

    @Override
    @Transactional
    public Optional<PromotionDraftRequest> claimNext() {
        return requests.claimNext(clock.instant(), LEASE_DURATION, MAX_ATTEMPTS);
    }

    @Override
    @Transactional
    public PromotionDraftRequest succeed(String requestId, String leaseToken, String promotionPostId) {
        PromotionDraftRequest request = load(requestId);
        request.succeed(leaseToken, promotionPostId, clock.instant());
        return requests.save(request);
    }

    @Override
    @Transactional
    public PromotionDraftRequest fail(String requestId, String leaseToken, String failureCode) {
        PromotionDraftRequest request = load(requestId);
        request.fail(leaseToken, failureCode, clock.instant());
        return requests.save(request);
    }

    private PromotionDraftRequest load(String requestId) {
        return requests.findById(requestId).orElseThrow(() -> new PromotionDraftRequestNotFoundException(requestId));
    }
}
