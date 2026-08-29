package com.gole.api.launch.domain.model;

/**
 * 서비스 공개 단계. 기능을 코드가 아니라 <b>운영 설정</b>으로 연다.
 *
 * <p>왜 단계인가. 이 서비스는 결제·정산처럼 외부 PG·지급대행 계약과 운영 검토에 묶인
 * 기능을 갖고 있어서, 계약이 확인된 순서대로만 열 수 있다. 기능 플래그를 개별로 흩어 두면
 * "지금 우리가 어디까지 연 상태인가"를 아무도 한눈에 답하지 못한다. 그래서 단계를 하나의
 * 축으로 두고, 예외가 필요할 때만 기능별 override를 얹는다.
 *
 * <p>단계는 되돌릴 수 있다. 사고가 나면 결제만 끄는 게 아니라 단계를 내려 한 번에 닫는다.
 */
public enum LaunchStage {

    /** 초기 공개. 매물·커뮤니티·채팅 직거래만 열고 결제는 닫는다. 조회 실패 시에도 이 값으로 fail-closed 한다. */
    PREPARING(0),

    /** 커뮤니티 성장 단계. 직거래 기능을 유지하고 결제는 계속 닫는다(기존 enum 이름은 호환을 위해 유지). */
    BROWSE_ONLY(1),

    /** 거래 개시. 결제가 열린다 — PortOne 설정이 실제로 준비됐을 때만 진입할 수 있다. */
    TRADING(2),

    /** 정식 운영. 자동 지급(파트너 정산)까지 연다. */
    FULL(3);

    private final int level;

    LaunchStage(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** 설정·API가 주고받는 정수(0~3)를 단계로 되돌린다. */
    public static LaunchStage ofLevel(int level) {
        for (LaunchStage stage : values()) {
            if (stage.level == level) {
                return stage;
            }
        }
        throw new IllegalArgumentException("알 수 없는 공개 단계: " + level);
    }

    public boolean atLeast(LaunchStage other) {
        return this.level >= other.level;
    }
}
