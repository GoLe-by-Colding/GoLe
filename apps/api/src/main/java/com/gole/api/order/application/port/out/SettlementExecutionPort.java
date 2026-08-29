package com.gole.api.order.application.port.out;

/**
 * Outbound port: 판매자에게 실제로 돈을 보내는 실행 수단.
 *
 * <p>원장 적재({@link SettlementPort})와 분리한 이유. 원장은 "얼마를 줘야 하는가"의 기록이고
 * 이건 "실제로 보냈는가"의 실행이다. 둘을 한 인터페이스에 두면 지급 수단이 없는 환경에서도
 * 원장이 실행처럼 보여서, 돈이 안 나갔는데 나간 것으로 읽히는 사고가 난다.
 *
 * <p>모드별 구현:
 *
 * <ul>
 *   <li>{@code MANUAL} — 구현체 없음. 운영자가 어드민 화면에서 배치로 확정한다.
 *   <li>{@code PROVIDER} — PG 지급대행 어댑터가 이 포트를 구현한다.
 *   <li>{@code DISABLED} — 구현체 없음. 자동 지급을 시도하지 않는다.
 * </ul>
 */
public interface SettlementExecutionPort {

    /**
     * 정산 1건을 실행한다. 같은 {@code orderId}로 여러 번 불려도 지급은 한 번만 나가야 한다
     * (멱등). 구현체는 외부 지급대행의 멱등키로 {@code orderId}를 쓴다.
     *
     * @return 지급 증빙 번호(외부 거래 ID). 원장에 그대로 기록된다.
     */
    String execute(String orderId, String sellerId, long payout);
}
