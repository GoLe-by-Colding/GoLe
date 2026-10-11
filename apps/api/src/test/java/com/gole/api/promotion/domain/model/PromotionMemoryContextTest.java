package com.gole.api.promotion.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionMemoryContextTest {
    private static List<PromotionMemoryContext.GuidelineSnapshot> guidelines(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new PromotionMemoryContext.GuidelineSnapshot(
                        "g" + i,
                        PromotionGuidelineKind.KNOWLEDGE,
                        "브랜드 사실 " + i,
                        List.of(PromotionMemoryTarget.IMAGE_EDIT),
                        List.of(PromotionCategory.SERVICE)))
                .toList();
    }

    @Test
    @DisplayName("여덟 건을 넘는 지침과 두 단계의 반려 여섯 건을 빠짐없이 보존한다")
    void stages_preserveCompleteMemory() {
        var first = new PromotionMemoryContext.StageSnapshot(
                PromotionMemoryContext.Stage.PLAN,
                List.of("/", "/collection"),
                List.of("f0", "f1", "f2"),
                List.of("g0"));
        var edit = new PromotionMemoryContext.StageSnapshot(
                PromotionMemoryContext.Stage.IMAGE_EDIT,
                List.of("/collection"),
                List.of("f3", "f4", "f5"),
                List.of("g8"));
        var context = new PromotionMemoryContext(
                List.of("f0", "f1", "f2", "f3", "f4", "f5"), guidelines(9), List.of(first, edit));
        assertThat(context.guidelines()).hasSize(9);
        assertThat(context.stages()).containsExactly(first, edit);
        assertThat(new PromotionMemoryContext(List.of(), List.of()).stages()).isEmpty();
    }

    @Test
    @DisplayName("단계별 예산 초과·중복 단계·원장에 없는 기억 참조를 거부한다")
    void stages_rejectInvalidReferencesAndBudgets() {
        assertThatThrownBy(() -> new PromotionMemoryContext.StageSnapshot(
                        PromotionMemoryContext.Stage.PLAN, List.of("/"), List.of("a", "b", "c", "d"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        var stage = new PromotionMemoryContext.StageSnapshot(
                PromotionMemoryContext.Stage.PLAN, List.of("/"), List.of("f1"), List.of("g0"));
        assertThatThrownBy(() -> new PromotionMemoryContext(List.of(), guidelines(1), List.of(stage)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromotionMemoryContext(List.of("f1"), guidelines(1), List.of(stage, stage)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
