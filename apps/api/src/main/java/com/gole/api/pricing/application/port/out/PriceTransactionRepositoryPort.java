package com.gole.api.pricing.application.port.out;

import com.gole.api.pricing.domain.model.PriceTransaction;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Outbound port: 체결 거래 영속성. (요구사항 9.1~9.5)
 *
 * <p>도메인 계층은 이 포트에만 의존하고, 구체 저장소(MongoDB 등)는 어댑터가 구현한다.
 */
public interface PriceTransactionRepositoryPort {

    /** 체결 거래를 저장한다. (요구사항 9.1) */
    PriceTransaction save(PriceTransaction transaction);

    /**
     * 특정 카탈로그 세트의 체결 거래를 체결 시각 오름차순으로 조회한다.
     *
     * <p>{@code from}/{@code to}가 {@code null}이면 해당 경계는 제한하지 않는다(전체 기간).
     */
    List<PriceTransaction> findInRangeAscending(String setNumber, Instant from, Instant to);

    /** 특정 세트·상태의 체결 거래를 체결 시각 오름차순으로 조회한다(상태별 시세용). */
    List<PriceTransaction> findByConditionAscending(
            String setNumber, com.gole.api.pricing.domain.model.SetCondition condition);

    /**
     * 특정 세트의 <b>여러 등급</b> 체결 거래를 한 번에 체결 시각 오름차순으로 조회한다.
     *
     * <p>등급별 표본이 모자랄 때 {@code ConditionGroup} 단위로 받치기 위한 조회다.
     * 등급마다 따로 부르면 그룹 하나당 N번 왕복하므로 묶어서 받는다.
     */
    List<PriceTransaction> findByConditionsAscending(
            String setNumber, List<com.gole.api.pricing.domain.model.SetCondition> conditions);

    /**
     * 체결 건수 기준 상위 세트를 집계한다(인기 랭킹). (백로그 13.4)
     *
     * @param limit 반환할 최대 세트 수
     * @param since 이 시각 이후의 체결만 집계. {@code null}이면 전체 기간.
     */
    List<TradeAggregate> findTopTradedSets(
            int limit,
            java.time.Instant since,
            Set<com.gole.api.pricing.domain.model.PriceTransactionSource> includedSources);

    /**
     * @param setNumber    세트 번호
     * @param tradeCount   체결 건수
     * @param averagePrice 평균 체결가(반올림 KRW)
     */
    record TradeAggregate(String setNumber, long tradeCount, long averagePrice) {}
}
