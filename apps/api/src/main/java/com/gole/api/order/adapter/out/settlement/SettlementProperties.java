package com.gole.api.order.adapter.out.settlement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 정산 실행 방식과 지급 유예 설정. {@code gole.settlement.*} 로 주입한다.
 *
 * <p><b>왜 모드를 명시하는가.</b> C2C 구매대금을 받아 개인 판매자에게 지급하는 구조는
 * 거래 구조와 판매자 유형에 맞는 PG·지급대행 계약 및 법률·세무 검토가 필요하다. 정확한 법적
 * 분류는 계약 구조별로 확인해야 하며, 내부 원장만으로 결제대금예치나 지급대행을 대체할 수 없다.
 * 그래서 안전 기본값은 {@link Mode#DISABLED}이고, 서면 계약 확인값까지 갖춰져야 결제 단계가 열린다.
 */
@Configuration
@ConfigurationProperties(prefix = "gole.settlement")
@Validated
public class SettlementProperties {

    /** 정산 실행 방식. */
    public enum Mode {
        /**
         * 원장만 쌓고 운영자가 배치로 확정(지급)한다. PG 계약·법률·세무 검토가 끝난 뒤
         * 운영자가 명시적으로 선택하는 모드다.
         * 구매확정 → 원장 PENDING 적재 → 관리자 배치 확정(PAID).
         */
        MANUAL,
        /**
         * 외부 지급대행(PG 파트너정산 등)이 판매자에게 직접 지급한다.
         * 실행은 {@code SettlementExecutionPort} 구현체가 담당한다.
         */
        PROVIDER,
        /**
         * 신규 플랫폼 결제와 정산을 모두 끈다. 공개 단계는 직거래 단계로 fail-closed 된다.
         * 단계 하향 전에 존재한 원장은 보존하되 <b>어떤 지급도 시도하지 않는다.</b>
         */
        DISABLED;

        /** 시스템이 판매자에게 돈을 보내려 시도해도 되는 모드인가. */
        public boolean allowsAutomaticPayout() {
            return this == PROVIDER;
        }
    }

    @NotNull
    private Mode mode = Mode.DISABLED;

    /**
     * PG/지급대행사와 현재 도메인·C2C 거래 모델·판매자 유형에 대한 서면 계약 확인 여부.
     *
     * <p>이 플래그는 계약을 대신하지 않는다. 운영자가 계약 근거를 확인한 뒤에만 켜는
     * fail-closed 확인값이며, false인 동안 Stage 2 이상과 모든 지급 실행은 잠긴다.
     */
    private boolean payoutContractVerified;

    /**
     * 구매확정(정산 원장 적재) 후 실제 지급까지 두는 유예 기간.
     *
     * <p>이체가 나간 뒤에 분쟁이 열리면 플랫폼에는 판매자에게서 돈을 회수할 수단이 없다.
     * 그래서 운영 검토와 외부 지급 결과 반영을 위한 추가 완충 시간을 둔다. 현재 주문 상태기는
     * 구매확정 뒤 새 분쟁 접수를 지원하지 않으므로, 이 값만으로 분쟁 창구가 닫혔다고 간주하면
     * 안 된다. 기본 3일은 최소 운영 완충값이고 실제 정책·약관과 함께 조정해야 한다.
     */
    @NotNull
    private Duration payoutHoldback = Duration.ofDays(3);

    /** 지급대행 호출 실패 뒤 같은 원장을 다시 시도하기까지의 간격. */
    @NotNull
    private Duration providerRetryAfter = Duration.ofMinutes(5);

    /** 실행 중 프로세스가 죽었을 때 선점을 회수할 수 있는 시간. 외부 호출은 주문 ID로 멱등하다. */
    @NotNull
    private Duration providerClaimTimeout = Duration.ofMinutes(10);

    /** 한 스케줄 주기에서 처리할 최대 건수. */
    @Min(1)
    private int providerBatchSize = 20;

    /** 같은 정산에 대해 외부 지급대행을 호출할 최대 횟수. 마지막 실패는 BLOCKED로 보내 사람이 확인한다. */
    @Min(1)
    private int providerMaxAttempts = 5;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isPayoutContractVerified() {
        return payoutContractVerified;
    }

    public void setPayoutContractVerified(boolean payoutContractVerified) {
        this.payoutContractVerified = payoutContractVerified;
    }

    public Duration getPayoutHoldback() {
        return payoutHoldback;
    }

    public void setPayoutHoldback(Duration payoutHoldback) {
        this.payoutHoldback = payoutHoldback;
    }

    public Duration getProviderRetryAfter() {
        return providerRetryAfter;
    }

    public void setProviderRetryAfter(Duration providerRetryAfter) {
        this.providerRetryAfter = providerRetryAfter;
    }

    public Duration getProviderClaimTimeout() {
        return providerClaimTimeout;
    }

    public void setProviderClaimTimeout(Duration providerClaimTimeout) {
        this.providerClaimTimeout = providerClaimTimeout;
    }

    public int getProviderBatchSize() {
        return providerBatchSize;
    }

    public void setProviderBatchSize(int providerBatchSize) {
        this.providerBatchSize = providerBatchSize;
    }

    public int getProviderMaxAttempts() {
        return providerMaxAttempts;
    }

    public void setProviderMaxAttempts(int providerMaxAttempts) {
        this.providerMaxAttempts = providerMaxAttempts;
    }

    @AssertTrue(message = "gole.settlement.payout-holdback은 0 이상이어야 합니다")
    public boolean isPayoutHoldbackValid() {
        return payoutHoldback != null && !payoutHoldback.isNegative();
    }

    @AssertTrue(message = "gole.settlement.provider-retry-after와 provider-claim-timeout은 양수여야 합니다")
    public boolean isProviderTimingValid() {
        return providerRetryAfter != null
                && !providerRetryAfter.isNegative()
                && !providerRetryAfter.isZero()
                && providerClaimTimeout != null
                && !providerClaimTimeout.isNegative()
                && !providerClaimTimeout.isZero();
    }
}
