package com.gole.api.collection.adapter.out.pricing;

import com.gole.api.collection.application.port.out.LatestPriceProviderPort;
import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import com.gole.api.pricing.domain.model.PriceStatistics;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 최근 체결가 조회 어댑터 (CROSS-CONTEXT). 컬렉션의 outbound port를 구현하되, Pricing 컨텍스트의
 * inbound port({@link GetPriceInsightsUseCase})에 의존해 시세 통계를 가져온다. 이는 깔끔한 헥사고날
 * 컨텍스트 간 연동이다: 컬렉션 어댑터 → Pricing 유스케이스.
 *
 * <p>{@code from}/{@code to}를 null로 전달해 전체 기간 통계를 조회하고, 통계가 있으면 최근 체결가
 * ({@link PriceStatistics#latestPrice()})를 반환한다. 거래가 없으면 비어있음.
 */
@Component
public class LatestPriceProviderAdapter implements LatestPriceProviderPort {

    private final GetPriceInsightsUseCase getPriceInsights;

    public LatestPriceProviderAdapter(GetPriceInsightsUseCase getPriceInsights) {
        this.getPriceInsights = getPriceInsights;
    }

    @Override
    public Optional<Long> latestPrice(String setNumber) {
        return getPriceInsights
                .getStatistics(setNumber, null, null)
                .map(PriceStatistics::latestPrice);
    }
}
