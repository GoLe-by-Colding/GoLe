package com.gole.api.promotion.application.port.out;

import com.gole.api.promotion.domain.model.PromotionRun;
import java.util.List;
import java.util.Optional;

/** 홍보 초안 에이전트 실행 원장 영속성 출력 포트. */
public interface PromotionRunRepositoryPort {

    Optional<PromotionRun> findByRunKey(String runKey);

    /**
     * 새 기록을 남긴다. 같은 {@code runKey} 가 동시에 먼저 저장됐으면(unique 충돌) 그 기록을
     * 돌려준다 — 호출 쪽은 둘 중 무엇이 저장됐는지 신경 쓰지 않는다.
     */
    PromotionRun insertIfAbsent(PromotionRun run);

    List<PromotionRun> findRecent(int limit);

    /** 지표 집계용 — 실행은 주 몇 건 수준이라 애플리케이션 레이어에서 reduce 한다. */
    List<PromotionRun> findAll();
}
