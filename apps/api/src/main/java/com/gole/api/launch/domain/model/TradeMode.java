package com.gole.api.launch.domain.model;

/**
 * 거래 모델 — <b>플랫폼이 돈을 만지는가</b>로 나뉜다.
 *
 * <p>이 축이 단계와 별도로 필요한 이유. 초기 단계는 "결제 기능이 아직 없는 에스크로"가 아니라
 * 애초에 다른 제품이다. 플랫폼이 대금을 보유하지 않고 판매자·구매자가 직접 만나 거래하는
 * 당근형 모델이며, 여기서 서버가 하는 일은 매물과 채팅을 잇는 것까지다. 이걸 "결제가 꺼진
 * 상태"로 부르면 주문·정산 원장이 있어야 할 것 같은 착시가 생기고, 실제로는 존재하지 않는
 * 거래 상태를 화면과 코드가 가정하게 된다.
 *
 * <p>돈을 직접 다루는 단계는 PG·지급대행 계약과 법률·세무·운영 검토가 끝난 뒤에만 연다.
 * 이 모델명만으로 어떤 규제 의무가 면제된다고 판단하지 않는다.
 */
public enum TradeMode {

    /**
     * 직거래. 플랫폼은 돈을 만지지 않는다. 매물·채팅·거래완료 양측 확인까지만 하고
     * 주문·결제·정산 API는 열지 않는다.
     */
    DIRECT_CHAT,

    /** 계약·검토 완료 후 플랫폼 결제를 받고 운영자가 정산 원장을 보고 직접 지급 확인한다. */
    MANUAL_SETTLEMENT,

    /** PG 지급대행이 판매자에게 직접 지급한다. */
    PARTNER_PAYOUT;

    /**
     * 단계별 기본 거래 모델.
     *
     * <p>0~1단계는 직거래, 2단계에서 플랫폼 결제가 열리고, 3단계에서 자동 지급까지 간다.
     */
    public static TradeMode defaultFor(LaunchStage stage) {
        return switch (stage) {
            case PREPARING, BROWSE_ONLY -> DIRECT_CHAT;
            case TRADING -> MANUAL_SETTLEMENT;
            case FULL -> PARTNER_PAYOUT;
        };
    }

    /** 이 모델에서 플랫폼이 주문·결제·정산을 다루는가. */
    public boolean platformHandlesMoney() {
        return this != DIRECT_CHAT;
    }
}
