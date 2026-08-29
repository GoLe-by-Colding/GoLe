package com.gole.api.launch.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 현재 공개 설정. 단계 하나와 기능별 예외(override)로 이루어진다.
 *
 * <p>override 를 "값 없음/켬/끔" 삼상태로 두는 이유. 단계 기본값과 같은 값을 굳이 저장하면
 * 나중에 단계 기본이 바뀌어도 옛 값이 눌러앉아 "단계를 올렸는데 안 열리는" 사고가 난다.
 * 지정하지 않은 기능은 언제나 단계 기본을 따라간다.
 */
public final class LaunchConfig {

    private final LaunchStage stage;
    private final Map<LaunchFeature, Boolean> overrides;
    private final Instant updatedAt;
    private final String updatedBy;
    private final Long version;

    public LaunchConfig(LaunchStage stage, Map<LaunchFeature, Boolean> overrides, Instant updatedAt, String updatedBy) {
        this(stage, overrides, updatedAt, updatedBy, null);
    }

    public LaunchConfig(
            LaunchStage stage,
            Map<LaunchFeature, Boolean> overrides,
            Instant updatedAt,
            String updatedBy,
            Long version) {
        this.stage = stage == null ? LaunchStage.PREPARING : stage;
        EnumMap<LaunchFeature, Boolean> copy = new EnumMap<>(LaunchFeature.class);
        if (overrides != null) {
            overrides.forEach((feature, enabled) -> {
                if (feature != null && enabled != null) {
                    copy.put(feature, enabled);
                }
            });
        }
        this.overrides = Collections.unmodifiableMap(copy);
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.version = version;
    }

    /**
     * 설정이 아직 저장되지 않았을 때의 안전한 기본값.
     *
     * <p>초기 GoLe는 돈을 보유하지 않고 매물과 대화를 잇는 당근형 직거래로 시작한다. 운영자가
     * PG와 정산 수단을 확인하고 명시적으로 단계를 올리기 전에는 주문·결제를 열지 않는다.
     */
    public static LaunchConfig unset() {
        return new LaunchConfig(LaunchStage.BROWSE_ONLY, Map.of(), null, null);
    }

    public LaunchStage stage() {
        return stage;
    }

    public Map<LaunchFeature, Boolean> overrides() {
        return overrides;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Long version() {
        return version;
    }

    /**
     * 이 단계의 거래 모델. 단계에서 파생되며 따로 저장하지 않는다.
     *
     * <p>저장하지 않는 이유: 저장하면 단계와 거래 모델이 어긋난 조합("2단계인데 직거래")이
     * 표현 가능해지고, 그 조합에서 주문 API를 열지 말지가 아무 데도 정의돼 있지 않다.
     */
    public TradeMode tradeMode() {
        return TradeMode.defaultFor(stage);
    }

    /** 플랫폼이 주문·결제·정산을 다루는 단계인가. 게이트가 이 값으로 판정한다. */
    public boolean platformHandlesMoney() {
        return tradeMode().platformHandlesMoney();
    }

    /** 이 기능이 지금 열려 있는가. override 가 있으면 그것이, 없으면 단계 기본이 답이다. */
    public boolean isEnabled(LaunchFeature feature) {
        boolean enabled = rawEnabled(feature);
        if (feature == LaunchFeature.PAYMENTS) {
            return stage.atLeast(LaunchStage.TRADING) && enabled;
        }
        if (feature == LaunchFeature.PARTNER_PAYOUT) {
            return stage.atLeast(LaunchStage.FULL) && enabled && isEnabled(LaunchFeature.PAYMENTS);
        }
        return enabled;
    }

    private boolean rawEnabled(LaunchFeature feature) {
        Boolean override = overrides.get(feature);
        return override != null ? override : feature.defaultEnabledAt(stage);
    }

    /** 공개 응답용 — 모든 기능의 최종 개방 여부. */
    public Map<LaunchFeature, Boolean> resolvedFeatures() {
        Map<LaunchFeature, Boolean> resolved = new LinkedHashMap<>();
        for (LaunchFeature feature : LaunchFeature.values()) {
            resolved.put(feature, isEnabled(feature));
        }
        return Collections.unmodifiableMap(resolved);
    }

    public LaunchConfig withStage(LaunchStage newStage, Instant at, String actorId) {
        return new LaunchConfig(newStage, overrides, at, actorId, version);
    }

    /** override 를 지정하거나({@code enabled != null}) 해제한다({@code enabled == null}). */
    public LaunchConfig withOverride(LaunchFeature feature, Boolean enabled, Instant at, String actorId) {
        // new EnumMap<>(map) 은 map 이 비어 있고 EnumMap 이 아니면 키 타입을 못 정해 예외를 던진다.
        // overrides 는 unmodifiableMap 으로 감싸져 있어 EnumMap 이 아니므로 클래스로 생성한 뒤 채운다.
        EnumMap<LaunchFeature, Boolean> next = new EnumMap<>(LaunchFeature.class);
        next.putAll(overrides);
        if (enabled == null) {
            next.remove(feature);
        } else {
            next.put(feature, enabled);
        }
        return new LaunchConfig(stage, next, at, actorId, version);
    }
}
