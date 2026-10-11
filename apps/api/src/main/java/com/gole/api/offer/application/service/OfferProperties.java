package com.gole.api.offer.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가격 제안 정책. (price-offer O5, O7, O12)
 *
 * <p>record가 아닌 세터 바인딩 클래스인 이유는 {@code PipelineProperties}와 같다 — AOP 프록시 대상이 될 수
 * 있는 빈은 final이면 CGLIB 서브클래싱이 안 돼 부팅이 실패한다.
 */
@ConfigurationProperties(prefix = "gole.offer")
public class OfferProperties {

    /** 대기 제안 만료. (기본 48시간) */
    private Duration pendingTtl = Duration.ofHours(48);

    /** 수락 제안 만료. 수락 시각부터 센다. (기본 72시간) */
    private Duration acceptedTtl = Duration.ofHours(72);

    /** 같은 매물에 같은 구매자가 24시간 안에 만들 수 있는 제안 수. (기본 5건) */
    private int maxPerListingPerDay = 5;

    public Duration pendingTtl() {
        return pendingTtl;
    }

    public Duration acceptedTtl() {
        return acceptedTtl;
    }

    /** 0 이하로 잘못 넣어도 제안이 영구히 막히지 않게 최소 1건으로 본다. */
    public int maxPerListingPerDay() {
        return Math.max(1, maxPerListingPerDay);
    }

    public void setPendingTtl(Duration pendingTtl) {
        this.pendingTtl = pendingTtl;
    }

    public void setAcceptedTtl(Duration acceptedTtl) {
        this.acceptedTtl = acceptedTtl;
    }

    public void setMaxPerListingPerDay(int maxPerListingPerDay) {
        this.maxPerListingPerDay = maxPerListingPerDay;
    }
}
