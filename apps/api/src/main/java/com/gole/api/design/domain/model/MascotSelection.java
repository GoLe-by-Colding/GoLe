package com.gole.api.design.domain.model;

import java.time.Instant;

/**
 * 마스코트 적용 리비전이자 그 감사 기록. 한 문서 insert라 적용과 기록이 따로 성공하지 않는다. (mascot-assets R3)
 *
 * <p>{@code assetName}은 적용 당시 이름의 스냅숏이다. 업로드 에셋을 지워도 이력에 무엇이었는지 남는다.
 */
public record MascotSelection(
        long revision,
        String assetId,
        String assetName,
        String actorId,
        String reason,
        String action,
        Instant publishedAt) {

    public static MascotSelection initial() {
        return new MascotSelection(
                0, MascotPresets.DEFAULT_KEY, MascotPresets.DEFAULT_NAME, "", "", "DEFAULT", Instant.EPOCH);
    }
}
