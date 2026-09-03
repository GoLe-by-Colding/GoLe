package com.gole.api.launch.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 결제·정산 개방 전에 운영자가 증빙을 확인해야 하는 수동 준비 항목.
 *
 * <p>환경변수 존재 여부처럼 서버가 자동 판정할 수 없는 사업·법무·실거래 검증을 영속 설정으로
 * 남긴다. 이 값은 법률 자문이나 계약 자체를 대신하지 않는다. 누가 언제 어떤 근거로 확인했는지
 * 변경 이력과 관리자 감사 로그를 남기고, 미확인 상태에서는 높은 공개 단계를 fail-closed 한다.
 */
public enum LaunchReadinessCheck {
    BUSINESS_DISCLOSURE("businessDisclosure", LaunchStage.TRADING),
    TERMS_PRIVACY("termsPrivacy", LaunchStage.TRADING),
    PAYMENT_FLOW("paymentFlow", LaunchStage.TRADING),
    PAYOUT_FLOW("payoutFlow", LaunchStage.FULL);

    private final String apiName;
    private final LaunchStage requiredFrom;

    LaunchReadinessCheck(String apiName, LaunchStage requiredFrom) {
        this.apiName = apiName;
        this.requiredFrom = requiredFrom;
    }

    public String apiName() {
        return apiName;
    }

    public LaunchStage requiredFrom() {
        return requiredFrom;
    }

    public boolean requiredAt(LaunchStage stage) {
        return stage.atLeast(requiredFrom);
    }

    public static Set<LaunchReadinessCheck> requiredFor(LaunchStage stage) {
        EnumSet<LaunchReadinessCheck> required = EnumSet.noneOf(LaunchReadinessCheck.class);
        for (LaunchReadinessCheck check : values()) {
            if (check.requiredAt(stage)) {
                required.add(check);
            }
        }
        return Set.copyOf(required);
    }

    public static LaunchReadinessCheck of(String raw) {
        if (raw != null) {
            for (LaunchReadinessCheck check : values()) {
                if (check.name().equalsIgnoreCase(raw) || check.apiName.equalsIgnoreCase(raw)) {
                    return check;
                }
            }
        }
        throw new IllegalArgumentException("알 수 없는 운영 준비 항목: " + raw);
    }
}
