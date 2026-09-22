package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 홍보 게시물 영속성 출력 포트.
 */
public interface PromotionPostRepositoryPort {

    PromotionPost save(PromotionPost promotionPost);

    Optional<PromotionPost> findById(String promotionPostId);

    /**
     * 아직 이 릴리스를 <b>점유 중인</b> 초안이 있는지 — 구현은 {@code claimedSourceCommitSha}를 본다.
     *
     * <p>반려된 초안은 점유를 놓아주므로(D2), 출처({@code sourceCommitSha})가 같아도 여기서는
     * 거짓이다. 이름은 인바운드 포트·{@code GET /exists} 계약과 맞추려고 그대로 뒀다.
     */
    boolean existsBySourceCommitSha(String sourceCommitSha);

    /** 같은 <b>출처</b> 릴리스로 만들어진 초안 수 — 반려된 것까지 센다. 재시도 상한 판정용. */
    long countBySourceCommitSha(String sourceCommitSha);

    List<PromotionPost> findRecentFirst(PromotionPostStatus status, int limit);

    long countByStatus(PromotionPostStatus status);

    /**
     * 검토 소요시간 집계용 — 제출·검토 시각이 <b>둘 다 있는</b> 게시물의 그 두 값만 읽는다.
     *
     * <p>지표가 쓰는 것이 이 두 필드뿐이라 전량 조회({@code findAll})를 이것으로 좁혔다. 예전
     * 구현은 caption·mediaUrls까지 끌고 와 도큐먼트와 도메인 객체를 컬렉션 크기만큼 동시에
     * 힙에 올렸는데, 보존 정책이 없어 단조 증가하는 컬렉션에 그 비용을 걸어 둘 이유가 없다.
     */
    List<ReviewTimestamps> findReviewTimestamps();

    /** 제출~검토 완료 구간. 둘 다 non-null인 것만 담긴다 — 소요시간 계산은 호출자 몫이다. */
    record ReviewTimestamps(Instant submittedAt, Instant reviewedAt) {}
}
