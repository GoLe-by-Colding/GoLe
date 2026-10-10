package com.gole.api.promotion.domain.model;

import static org.assertj.core.api.Assertions.*;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionGuidelineTest {
    private PromotionGuideline proposed() {
        return new PromotionGuideline(
                "g1",
                PromotionGuidelineKind.PROCEDURE,
                "화면을 과도하게 축소하지 않는다",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.SERVICE),
                List.of("f1"),
                PromotionGuidelineStatus.PROPOSED,
                "agent",
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "run-1");
    }

    @Test
    @DisplayName("수정한 사람은 확정할 수 있고 원 제안자는 수정 후에도 확정할 수 없다")
    void edit_preservesMakerAndAllowsOtherReviewer() {
        var edited = proposed()
                .edit(
                        "가독성을 우선한다",
                        List.of(PromotionMemoryTarget.IMAGE_EDIT),
                        List.of(PromotionCategory.SERVICE, PromotionCategory.FEATURE),
                        Instant.EPOCH.plusSeconds(1));
        assertThat(edited.proposedBy()).isEqualTo("agent");
        assertThatThrownBy(() -> edited.activate("agent", Instant.EPOCH.plusSeconds(2)))
                .isInstanceOf(ForbiddenException.class);
        var active = edited.activate("human", Instant.EPOCH.plusSeconds(2));
        assertThat(active.status()).isEqualTo(PromotionGuidelineStatus.ACTIVE);
        assertThat(active.confirmedBy()).isEqualTo("human");
        assertThat(active.activate("human", Instant.EPOCH.plusSeconds(3))).isEqualTo(active);
    }

    @Test
    @DisplayName("활성 지침은 편집/기각할 수 없고 해제 뒤 다시 활성화할 수 없다")
    void lifecycle_rejectsInvalidTransitions() {
        var active = proposed().activate("human", Instant.EPOCH);
        assertThatThrownBy(() -> active.edit("수정", active.targets(), active.categories(), Instant.EPOCH))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> active.dismiss(Instant.EPOCH)).isInstanceOf(ConflictException.class);
        var retired = active.retire(Instant.EPOCH.plusSeconds(1));
        assertThat(retired.retire(Instant.EPOCH.plusSeconds(2))).isEqualTo(retired);
        assertThatThrownBy(() -> retired.activate("human", Instant.EPOCH)).isInstanceOf(ConflictException.class);
        var dismissed = proposed().dismiss(Instant.EPOCH);
        assertThat(dismissed.dismiss(Instant.EPOCH)).isEqualTo(dismissed);
    }

    @Test
    @DisplayName("빈/중복 적용 범위와 과도한 내용은 거부한다")
    void scopeAndContent_areBounded() {
        assertThatThrownBy(() ->
                        proposed().edit(" ", proposed().targets(), proposed().categories(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proposed()
                        .edit("x".repeat(1001), proposed().targets(), proposed().categories(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proposed().edit("내용", List.of(), proposed().categories(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proposed()
                        .edit(
                                "내용",
                                List.of(PromotionMemoryTarget.CAPTION, PromotionMemoryTarget.CAPTION),
                                proposed().categories(),
                                Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
