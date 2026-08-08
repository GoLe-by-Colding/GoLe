package com.gole.api.order.adapter.out.settlement;

import com.gole.api.order.domain.model.FeePolicy;
import com.gole.api.order.domain.model.Settlement;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 플랫폼 수수료 설정. (shipping-and-fees R5.1)
 *
 * <p>{@code gole.fee.*} 로 주입한다. 미설정 시 기존 동작(5%, 하한·상한 없음)과 동일하다.
 */
@Configuration
@ConfigurationProperties(prefix = "gole.fee")
public class FeeProperties {

    /** 수수료율(0.0~1.0). 기본값은 기존 하드코딩 값과 동일하다. */
    @SuppressWarnings("deprecation")
    private double rate = Settlement.PLATFORM_FEE_RATE;

    /** 최소 수수료(원). 0이면 하한 없음. */
    private long minFee = 0;

    /** 최대 수수료(원). 0이면 상한 없음. */
    private long maxFee = 0;

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public long getMinFee() {
        return minFee;
    }

    public void setMinFee(long minFee) {
        this.minFee = minFee;
    }

    public long getMaxFee() {
        return maxFee;
    }

    public void setMaxFee(long maxFee) {
        this.maxFee = maxFee;
    }

    /** 설정값을 도메인 정책 객체로 노출한다. 검증은 {@link FeePolicy} 불변식이 담당한다. */
    @Bean
    public FeePolicy feePolicy() {
        return new FeePolicy(rate, minFee, maxFee);
    }
}
