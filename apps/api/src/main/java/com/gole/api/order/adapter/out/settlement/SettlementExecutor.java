package com.gole.api.order.adapter.out.settlement;

import com.gole.api.order.adapter.out.settlement.SettlementProperties.Mode;
import com.gole.api.order.application.port.out.SettlementExecutionPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * {@code gole.settlement.mode}를 실제 동작으로 옮긴다.
 *
 * <p>설정값이 런타임을 바꾸지 않으면 오설정이 조용히 넘어간다 — {@code PROVIDER}로 켜 뒀는데
 * 지급 어댑터가 없어서 아무도 돈을 안 보내는 상황이 대표적이다. 그래서 부팅 시점에
 * 모드와 구현체 조합을 검증해 <b>뜨지 않게</b> 만든다(fail fast).
 *
 * <p>지급 실행은 모드마다 다르다.
 *
 * <ul>
 *   <li>{@code MANUAL} — 시스템은 지급하지 않는다. 운영자가 어드민에서 배치로 확정한다.
 *   <li>{@code PROVIDER} — {@link SettlementExecutionPort} 구현체가 지급하고 증빙을 돌려준다.
 *   <li>{@code DISABLED} — 원장만 남기고 <b>자동 지급을 시도하지 않는다.</b>
 * </ul>
 */
@Component
public class SettlementExecutor implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SettlementExecutor.class);

    private final SettlementProperties properties;
    private final Optional<SettlementExecutionPort> execution;

    public SettlementExecutor(SettlementProperties properties, Optional<SettlementExecutionPort> execution) {
        this.properties = properties;
        this.execution = execution;
    }

    /**
     * 오설정을 부팅에서 막는다.
     *
     * <p>PROVIDER인데 지급 어댑터가 없으면 판매자에게 영원히 돈이 안 간다. 이건 런타임에
     * 발견하면 이미 늦은 종류의 사고라서 애플리케이션을 아예 띄우지 않는다.
     */
    @Override
    public void afterPropertiesSet() {
        Mode mode = properties.getMode();
        if (mode == null) {
            throw new IllegalStateException("gole.settlement.mode를 지정해야 합니다.");
        }
        if (properties.getPayoutHoldback() == null
                || properties.getPayoutHoldback().isNegative()) {
            throw new IllegalStateException("gole.settlement.payout-holdback은 0 이상의 기간이어야 합니다.");
        }
        if (!properties.isProviderTimingValid()
                || properties.getProviderBatchSize() < 1
                || properties.getProviderMaxAttempts() < 1) {
            throw new IllegalStateException("지급대행 재시도·선점 시간은 양수이고 batch-size와 max-attempts는 1 이상이어야 합니다.");
        }
        if (mode == Mode.PROVIDER && execution.isEmpty()) {
            throw new IllegalStateException("gole.settlement.mode=PROVIDER 인데 SettlementExecutionPort 구현체가 없습니다. "
                    + "지급대행 어댑터를 등록하거나 mode를 MANUAL로 되돌리세요.");
        }
        if (mode != Mode.PROVIDER && execution.isPresent()) {
            throw new IllegalStateException(
                    "gole.settlement.mode=%s 인데 SettlementExecutionPort 구현체가 등록돼 있습니다. ".formatted(mode)
                            + "자동 지급이 의도된 것이면 mode를 PROVIDER로 올리세요.");
        }
        if (mode != Mode.DISABLED && !properties.isPayoutContractVerified()) {
            log.warn("정산 모드는 {}이지만 지급대행 계약 확인값이 false라 모든 지급 실행을 잠급니다", mode);
        }
        log.info(
                "정산 모드 {} — 자동 지급 {}, 지급 유예 {}",
                mode,
                mode.allowsAutomaticPayout() ? "사용" : "안 함",
                properties.getPayoutHoldback());
    }

    /** 이 모드에서 시스템이 판매자에게 돈을 보낼 수 있는가. */
    public boolean canPayAutomatically() {
        return properties.isPayoutContractVerified()
                && properties.getMode().allowsAutomaticPayout()
                && execution.isPresent();
    }

    /**
     * 구매확정 직후 자동 지급을 시도한다. 자동 지급이 없는 모드에서는 아무것도 하지 않는다
     * — 원장은 이미 {@code SettlementPort}가 적재했으므로 돈만 안 나갈 뿐 기록은 남는다.
     *
     * @return 지급 증빙 번호. 자동 지급을 하지 않았으면 비어 있다.
     */
    public Optional<String> payIfAutomatic(String orderId, String sellerId, long payout) {
        if (!canPayAutomatically()) {
            return Optional.empty();
        }
        String reference = execution.orElseThrow().execute(orderId, sellerId, payout);
        log.info("정산 자동 지급 완료 orderId={} sellerId={} payout={} ref={}", orderId, sellerId, payout, reference);
        return Optional.ofNullable(reference);
    }
}
