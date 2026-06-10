package com.gole.api.pricing.adapter.in.web;

import com.gole.api.pricing.adapter.in.web.PricingResponses.PricePointResponse;
import com.gole.api.pricing.adapter.in.web.PricingResponses.StatisticsResponse;
import com.gole.api.pricing.adapter.in.web.PricingResponses.ValuationResponse;
import com.gole.api.pricing.application.port.in.GetPriceInsightsUseCase;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 카탈로그 세트 시세 조회. (요구사항 9)
 */
@RestController
@RequestMapping("/api/v1/pricing/sets/{setNumber}")
public class PricingController {

    private final GetPriceInsightsUseCase getPriceInsightsUseCase;

    public PricingController(GetPriceInsightsUseCase getPriceInsightsUseCase) {
        this.getPriceInsightsUseCase = getPriceInsightsUseCase;
    }

    @GetMapping("/statistics")
    public StatisticsResponse statistics(
            @PathVariable String setNumber,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to) {
        return getPriceInsightsUseCase
                .getStatistics(setNumber, from, to)
                .map(StatisticsResponse::of)
                .orElseGet(() -> StatisticsResponse.noData(setNumber));
    }

    @GetMapping("/chart")
    public List<PricePointResponse> chart(
            @PathVariable String setNumber, @RequestParam(value = "condition", required = false) String condition) {
        // 헤드라인 시장 차트는 미개봉(new_sealed) 기준. condition 지정 시 해당 상태 시계열.
        com.gole.api.pricing.domain.model.SetCondition c =
                com.gole.api.pricing.domain.model.SetCondition.fromKey(condition);
        return getPriceInsightsUseCase.getChart(setNumber, c).stream()
                .map(PricePointResponse::from)
                .toList();
    }

    @GetMapping("/history")
    public List<PricePointResponse> history(@PathVariable String setNumber) {
        return getPriceInsightsUseCase.getHistory(setNumber).stream()
                .map(PricePointResponse::from)
                .toList();
    }

    @GetMapping("/valuation")
    public ValuationResponse valuation(@PathVariable String setNumber) {
        return getPriceInsightsUseCase
                .getValuation(setNumber)
                .map(ValuationResponse::of)
                .orElseGet(() -> ValuationResponse.noData(setNumber));
    }
}
