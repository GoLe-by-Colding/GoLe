package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionDraftRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 홍보 초안 요청 영속성 출력 포트. */
public interface PromotionDraftRequestRepositoryPort {

    PromotionDraftRequest save(PromotionDraftRequest request);

    Optional<PromotionDraftRequest> findById(String requestId);

    /**
     * 대기 중이거나 lease 가 만료된 요청 하나를 <b>원자적으로</b> 점유한다.
     *
     * <p>두 실행이 같은 요청을 집으면 같은 릴리스로 초안이 둘 생긴다. 조회 후 갱신으로는 못
     * 막으므로 구현은 단일 {@code findAndModify} 여야 한다
     * ({@code MongoSupportNotificationOutboxAdapter.claimNext} 와 같은 방식).
     *
     * <p>시도 수가 상한을 넘긴 항목은 집지 않고 FAILED 로 닫는다.
     */
    Optional<PromotionDraftRequest> claimNext(Instant now, Duration leaseDuration, int maximumAttempts);

    /** 아직 끝나지 않은 같은 커밋 요청 — 같은 요청을 두 번 쌓지 않으려고 본다. */
    Optional<PromotionDraftRequest> findActiveBySourceCommitSha(String sourceCommitSha);

    /** 커밋을 지정하지 않은(자동 선정) 요청 중 아직 끝나지 않은 것. */
    Optional<PromotionDraftRequest> findActiveWithoutSourceCommit();

    List<PromotionDraftRequest> findRecentFirst(int limit);
}
