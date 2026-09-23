package com.gole.api.promotion.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromotionPostMongoRepository extends MongoRepository<PromotionPostDocument, String> {

    List<PromotionPostDocument> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    boolean existsByClaimedSourceCommitSha(String claimedSourceCommitSha);

    long countBySourceCommitSha(String sourceCommitSha);

    List<PromotionPostDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    /**
     * 검토 소요시간 집계용. 반환 타입이 닫힌 인터페이스 프로젝션이라 Spring Data 가 두 필드만
     * 읽어 온다 — 지표에 쓰지 않는 caption·mediaUrls 는 네트워크에도 힙에도 올라오지 않는다.
     *
     * <p>{@code NotNull} 은 {@code $ne: null} 로 번역되며, MongoDB 에서 이 조건은 값이 null 인
     * 것뿐 아니라 <b>필드가 없는</b> 도큐먼트도 함께 제외한다. 아직 제출·검토를 거치지 않은
     * 초안이 걸러지는 근거다.
     */
    List<ReviewTimestampsProjection> findBySubmittedAtNotNullAndReviewedAtNotNull();

    interface ReviewTimestampsProjection {
        Instant getSubmittedAt();

        Instant getReviewedAt();
    }
}
