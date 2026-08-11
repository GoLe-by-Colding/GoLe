package com.gole.api.pricing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SetConditionTest {

    @Test
    void everyGradeBelongsToExactlyOneGroup() {
        for (SetCondition condition : SetCondition.values()) {
            assertThat(condition.group()).isNotNull();
            assertThat(condition.group().members()).contains(condition);
        }
        // 그룹 명단의 합집합이 전체 등급과 정확히 일치해야 한다(누락·중복 없음).
        assertThat(Arrays.stream(ConditionGroup.values())
                        .flatMap(g -> g.members().stream())
                        .toList())
                .containsExactlyInAnyOrder(SetCondition.values());
    }

    @Test
    void groupBoundariesMatchTheLegacyThreeGrades() {
        // 그룹 경계를 3단계와 맞춰 둬야 레거시 체결 이력이 올바른 그룹에 그대로 떨어진다.
        assertThat(ConditionGroup.SEALED.members()).containsExactly(SetCondition.NEW_SEALED);
        assertThat(ConditionGroup.COMPLETE.members()).containsExactly(SetCondition.LIKE_NEW, SetCondition.USED_GOOD);
        assertThat(ConditionGroup.INCOMPLETE.members()).containsExactly(SetCondition.USED_FAIR, SetCondition.DAMAGED);
    }

    @Test
    void referenceFactorIsTheMeanOfMemberFactors() {
        assertThat(ConditionGroup.SEALED.referenceFactor()).isCloseTo(1.00, within(1e-9));
        assertThat(ConditionGroup.COMPLETE.referenceFactor()).isCloseTo((0.88 + 0.78) / 2, within(1e-9));
        assertThat(ConditionGroup.INCOMPLETE.referenceFactor()).isCloseTo((0.62 + 0.45) / 2, within(1e-9));
    }

    @Test
    void factorsDescendMonotonicallyByGrade() {
        double previous = Double.MAX_VALUE;
        for (SetCondition condition : SetCondition.values()) {
            assertThat(condition.factor()).isLessThan(previous);
            previous = condition.factor();
        }
    }

    @Test
    void fromKeyMapsLegacyKeys() {
        assertThat(SetCondition.fromKey("used_complete")).isEqualTo(SetCondition.USED_GOOD);
        assertThat(SetCondition.fromKey("used_incomplete")).isEqualTo(SetCondition.USED_FAIR);
        assertThat(SetCondition.fromKey("USED_COMPLETE")).isEqualTo(SetCondition.USED_GOOD);
    }

    @Test
    void fromKeyTreatsUntaggedAsSealed() {
        // 상태 태깅 이전 체결가는 헤드라인 시세(미개봉 기준)로 쓰이던 값이다. 해석을 바꾸지 않는다.
        assertThat(SetCondition.fromKey(null)).isEqualTo(SetCondition.NEW_SEALED);
        assertThat(SetCondition.fromKey("")).isEqualTo(SetCondition.NEW_SEALED);
    }

    @Test
    void storageKeysIncludeLegacyKeysSoOldTradesStayVisible() {
        assertThat(SetCondition.USED_GOOD.storageKeys()).containsExactlyInAnyOrder("used_good", "used_complete");
        assertThat(SetCondition.USED_FAIR.storageKeys()).containsExactlyInAnyOrder("used_fair", "used_incomplete");
        assertThat(SetCondition.NEW_SEALED.storageKeys()).containsExactly("new_sealed");
        assertThat(SetCondition.LIKE_NEW.storageKeys()).containsExactly("like_new");
        assertThat(SetCondition.DAMAGED.storageKeys()).containsExactly("damaged");
    }
}
