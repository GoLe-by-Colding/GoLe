package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.ChatBlock;
import java.util.Collection;
import java.util.List;

/** Outbound port: 사용자 차단 저장소. */
public interface ChatBlockRepositoryPort {

    void save(ChatBlock block);

    void delete(String blockerId, String blockedId);

    /** 두 사람 사이에 차단이 있는가. 방향은 묻지 않는다 — 어느 쪽이 걸었든 막힌다. */
    boolean blockedBetween(String a, String b);

    /** 두 집합 사이에 차단된 쌍이 하나라도 있는가. 그룹 생성·초대의 단일 배치 조회에 쓴다. */
    boolean blockedBetweenAny(Collection<String> leftAccountIds, Collection<String> rightAccountIds);

    /** 이 사람과 차단 관계인 상대들. 목록 필터링에 쓴다. */
    List<String> blockedCounterparts(String accountId);

    /** 이 사용자가 직접 차단한 상대들. 차단 관리 UI에는 반대 방향 차단을 노출하지 않는다. */
    List<String> blockedTargets(String blockerId);
}
