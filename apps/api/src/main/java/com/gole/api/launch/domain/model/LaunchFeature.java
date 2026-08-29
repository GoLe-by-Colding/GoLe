package com.gole.api.launch.domain.model;

/**
 * 단계로 열리고 닫히는 기능. 각 기능은 "몇 단계부터 기본으로 열리는가"를 스스로 안다.
 *
 * <p>기본값을 기능 쪽에 두는 이유: 단계가 늘어날 때 조건문이 흩어지지 않게 하려는 것이다.
 * 운영 중 예외가 필요하면 {@link LaunchConfig}의 override로 단건만 뒤집는다.
 */
public enum LaunchFeature {

    /** 결제. 외부 PG 설정이 준비돼야 하므로 {@link LaunchStage#TRADING}부터. */
    PAYMENTS("payments", LaunchStage.TRADING),

    /** 리뷰. 거래가 있어야 쓸 대상이 생기므로 결제와 같이 열린다. */
    REVIEWS("reviews", LaunchStage.TRADING),

    /** 파트너 자동 지급. PG 지급대행 계약이 전제라 마지막에 열린다. */
    PARTNER_PAYOUT("partnerPayout", LaunchStage.FULL);

    private final String apiName;
    private final LaunchStage enabledFrom;

    LaunchFeature(String apiName, LaunchStage enabledFrom) {
        this.apiName = apiName;
        this.enabledFrom = enabledFrom;
    }

    /** 공개 API 응답의 키. 프론트 계약이므로 enum 이름과 분리해 고정한다. */
    public String apiName() {
        return apiName;
    }

    public LaunchStage enabledFrom() {
        return enabledFrom;
    }

    /** override 가 없을 때 이 단계에서의 기본 개방 여부. */
    public boolean defaultEnabledAt(LaunchStage stage) {
        return stage.atLeast(enabledFrom);
    }

    /** 설정 저장값·API 입력을 기능으로 되돌린다. enum 이름과 apiName 을 모두 받는다. */
    public static LaunchFeature of(String raw) {
        if (raw != null) {
            for (LaunchFeature feature : values()) {
                if (feature.name().equalsIgnoreCase(raw) || feature.apiName.equalsIgnoreCase(raw)) {
                    return feature;
                }
            }
        }
        throw new IllegalArgumentException("알 수 없는 기능: " + raw);
    }
}
