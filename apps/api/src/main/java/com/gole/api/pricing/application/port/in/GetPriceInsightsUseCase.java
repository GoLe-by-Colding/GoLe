package com.gole.api.pricing.application.port.in;

import com.gole.api.pricing.domain.model.PriceSnapshot;
import com.gole.api.pricing.domain.model.PriceStatistics;
import com.gole.api.pricing.domain.model.PriceTransaction;
import com.gole.api.pricing.domain.model.PriceValuation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: 카탈로그 세트 시세 조회(통계/차트/체결내역/상태별 밸류에이션). (요구사항 9.2~9.5)
 * from/to가 null이면 전체 기간.
 */
public interface GetPriceInsightsUseCase {

    /** 표본 단계와 provenance를 포함한 일관된 공개 시세 스냅샷. */
    PriceSnapshot getSnapshot(String setNumber);

    /** 통계. 검증된 체결이 최소 표본(현재 3건)에 못 미치면 empty(요구사항 9.5). */
    Optional<PriceStatistics> getStatistics(String setNumber, Instant from, Instant to);

    /** 차트용 시계열(시간 오름차순). (요구사항 9.3) */
    List<PriceTransaction> getChart(String setNumber, Instant from, Instant to);

    /** 상태별 차트 시계열(시간 오름차순). 헤드라인 시장 차트는 미개봉 기준. */
    List<PriceTransaction> getChart(String setNumber, com.gole.api.pricing.domain.model.SetCondition condition);

    /** 체결 내역(최신→오래된 순). (요구사항 9.4) */
    List<PriceTransaction> getHistory(String setNumber);

    /** 최근 체결가 기준 상태별 시세 밸류에이션(매수/매도/감가). 거래가 없으면 empty. */
    Optional<PriceValuation> getValuation(String setNumber);
}
