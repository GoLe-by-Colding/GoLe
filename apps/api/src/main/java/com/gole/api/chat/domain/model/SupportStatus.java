package com.gole.api.chat.domain.model;

/**
 * 운영팀 문의방의 처리 상태. 인박스 필터가 그대로 이 값이다.
 *
 * <p>상태 전이를 도메인에 고정하는 이유: "완료된 문의가 배정만 바뀌어 다시 진행중이 되는" 식의
 * 조합을 화면 쪽 분기로 막으면, 화면이 늘어날 때마다 같은 규칙을 다시 써야 한다.
 */
public enum SupportStatus {

    /** 사용자가 만들었고 아직 아무도 안 가져갔다. */
    UNASSIGNED,

    /** 담당 관리자가 있고 우리 차례다. */
    IN_PROGRESS,

    /** 관리자가 답했고 사용자 응답을 기다린다. */
    WAITING_USER,

    /** 종결. 재개하면 다시 진행중이 된다. */
    RESOLVED;

    public boolean canAssign() {
        return this != RESOLVED;
    }

    public boolean isOpen() {
        return this != RESOLVED;
    }
}
