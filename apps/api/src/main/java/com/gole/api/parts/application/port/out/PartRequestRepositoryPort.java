package com.gole.api.parts.application.port.out;

import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import java.util.List;
import java.util.Optional;

/** 부품 요청 영속성 outbound port. 목록은 모두 최신순(createdAt 내림차순)이다. */
public interface PartRequestRepositoryPort {

    PartRequest save(PartRequest request);

    Optional<PartRequest> findById(String requestId);

    /**
     * 열린 요청일 때만 마감 상태를 반영한다. 그 사이 다른 요청이 마감·삭제했으면 {@code false} —
     * 통째로 덮어쓰면 방금 지운 요청이 되살아난다.
     */
    boolean saveClosureIfOpen(PartRequest closed);

    /** 작성자의 열린 요청 수. (W2 열린 요청 상한) */
    long countOpenByRequester(String requesterId);

    /**
     * 게시판 조회.
     *
     * @param setNumber 세트 번호(선택, {@code null}이면 전체)
     * @param status    상태(선택, {@code null}이면 상태 무관)
     */
    List<PartRequest> search(String setNumber, PartRequestStatus status, int limit);

    List<PartRequest> findByRequester(String requesterId, int limit);

    void deleteById(String requestId);
}
