package com.gole.api.order.domain.model;

/**
 * 플랫폼 수수료 정책. (shipping-and-fees R5)
 *
 * <p>요율을 도메인 상수로 박아두면 요율 변경·면제·상한 같은 정책 조정이 배포 없이는 불가능하다.
 * 정책을 값 객체로 분리해 설정에서 주입한다.
 *
 * @param rate   수수료율 (0.0 ~ 1.0). 예: 0.05 = 5%
 * @param minFee 최소 수수료(원). 소액 거래에서 수수료가 0원이 되는 것을 막는다.
 * @param maxFee 최대 수수료(원). 고액 거래의 수수료 상한. 0이면 상한 없음.
 */
public record FeePolicy(double rate, long minFee, long maxFee) {

    public FeePolicy {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException("수수료율은 0.0~1.0 범위여야 합니다: " + rate);
        }
        if (minFee < 0 || maxFee < 0) {
            throw new IllegalArgumentException("수수료 하한·상한은 음수일 수 없습니다");
        }
        if (maxFee > 0 && minFee > maxFee) {
            throw new IllegalArgumentException("수수료 하한이 상한보다 클 수 없습니다: " + minFee + " > " + maxFee);
        }
    }

    /**
     * 거래액에 대한 수수료를 계산한다. 원 단위 정수로 반올림한 뒤 하한·상한을 적용한다. (R5.4)
     *
     * <p>수수료가 거래액을 넘지 않도록 마지막에 클램프한다. minFee가 거래액보다 큰
     * 소액 거래에서 정산액이 음수가 되는 것을 막기 위한 방어다.
     */
    public long feeFor(long gross) {
        if (gross <= 0) {
            return 0;
        }
        long fee = Math.round(gross * rate);
        if (fee < minFee) {
            fee = minFee;
        }
        if (maxFee > 0 && fee > maxFee) {
            fee = maxFee;
        }
        return Math.min(fee, gross);
    }
}
