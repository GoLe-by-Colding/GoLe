package com.gole.api.promotion.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * 검토자가 초안과 함께 봐야 하는 맥락 — 종류, 스크린샷 설명표, 출처. 사람이 콘솔에서 직접 쓴
 * 글은 설명표와 출처가 없다({@link #NONE}).
 */
public record PromotionPostContext(
        PromotionCategory category, List<PromotionCapture> captures, PromotionProvenance provenance) {

    public static final PromotionPostContext NONE =
            new PromotionPostContext(PromotionCategory.FEATURE, List.of(), null);

    public PromotionPostContext {
        category = category == null ? PromotionCategory.FEATURE : category;
        captures = captures == null ? List.of() : List.copyOf(captures);
        captures.forEach(capture -> Objects.requireNonNull(capture, "capture"));
    }
}
