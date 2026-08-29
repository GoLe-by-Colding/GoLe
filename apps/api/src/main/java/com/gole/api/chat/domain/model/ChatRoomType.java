package com.gole.api.chat.domain.model;

/**
 * 방 유형. 유형마다 참여 규칙과 허용 동작이 다르다.
 *
 * <p>유형을 필드로 두고 규칙을 여기에 모으는 이유: 규칙이 다른 방을 같은 자료구조로 뭉개면
 * "이 방에서 직거래 완료를 눌러도 되나" 같은 판단을 호출부마다 다시 하게 되고, 한 곳만
 * 빠뜨려도 조용히 잘못 열린다.
 */
public enum ChatRoomType {

    /** 사용자 2명의 1:1 대화. 같은 상대와는 항상 같은 방이다. */
    DIRECT(2, 2),

    /** 3명 이상이 모이는 방. 방장과 제목이 있다. 2명이면 그건 DIRECT 다. */
    GROUP(3, 50),

    /** 매물 문의방(buyer + seller + listingId). 직거래 완료 확인이 가능한 유일한 유형이다. */
    LISTING(2, 2),

    /** 운영팀 문의방. 사용자 1명으로 시작하고 배정된 관리자가 멤버로 합류한다. */
    SUPPORT(1, Integer.MAX_VALUE);

    private final int minMembers;
    private final int maxMembers;

    ChatRoomType(int minMembers, int maxMembers) {
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
    }

    /**
     * 생성 시 필요한 최소 인원.
     *
     * <p><b>생성 시점에만</b> 적용한다. 이미 만들어진 방에는 강요하지 않는다 — 3인 그룹에서
     * 한 명이 나갔다고 방이 깨지거나 나가기가 막히면 안 된다. 저장된 상태는 언제나 다시
     * 읽을 수 있어야 하므로 재구성 경로에서는 검사하지 않는다.
     */
    public int minMembers() {
        return minMembers;
    }

    public int maxMembers() {
        return maxMembers;
    }

    /**
     * 직거래 완료 확인이 가능한가.
     *
     * <p>완료 확인은 "이 매물의 거래가 끝났다"는 선언이라 대상 매물이 없으면 무엇이 끝났는지
     * 지목할 수 없다. 완료가는 시세 집계 후보이므로 대상 없는 완료는 곧 데이터 오염이다.
     */
    public boolean allowsDirectTradeConfirmation() {
        return this == LISTING;
    }

    /** 관리자가 멤버로 참여할 수 있는 유형인가. 운영 권한은 사생활 열람권이 아니다. */
    public boolean allowsAdminParticipation() {
        return this == SUPPORT;
    }

    /** 멤버를 초대로 늘릴 수 있는가. */
    public boolean allowsInvitation() {
        return this == GROUP;
    }

    /** 저장된 값이 없으면 매물 방으로 읽는다 — 이 필드가 생기기 전의 문서는 전부 매물 방이다. */
    public static ChatRoomType ofNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return LISTING;
        }
        return valueOf(raw);
    }
}
