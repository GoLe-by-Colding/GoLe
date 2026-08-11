package com.gole.api.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ItemConditionTest {

    @Test
    void parseKeyAcceptsCurrentKeysCaseInsensitively() {
        assertThat(ItemCondition.parseKey("new_sealed")).contains(ItemCondition.NEW_SEALED);
        assertThat(ItemCondition.parseKey("LIKE_NEW")).contains(ItemCondition.LIKE_NEW);
        assertThat(ItemCondition.parseKey("  used_good  ")).contains(ItemCondition.USED_GOOD);
        assertThat(ItemCondition.parseKey("Damaged")).contains(ItemCondition.DAMAGED);
    }

    @Test
    void parseKeyMapsLegacyThreeGradeValues() {
        // condition-disclosure 마이그레이션 매핑. 저장된 문서를 일괄 변환하지 않고 읽기 시점에 흡수한다.
        assertThat(ItemCondition.parseKey("used_complete")).contains(ItemCondition.USED_GOOD);
        assertThat(ItemCondition.parseKey("USED_INCOMPLETE")).contains(ItemCondition.USED_FAIR);
    }

    @Test
    void parseKeyRejectsUnknownAndBlank() {
        // 입력 경로에서 오타를 조용히 흡수하면 사용자는 걸리지도 않은 필터를 신뢰하게 된다.
        assertThat(ItemCondition.parseKey("nonsense")).isEmpty();
        assertThat(ItemCondition.parseKey("")).isEmpty();
        assertThat(ItemCondition.parseKey("   ")).isEmpty();
        assertThat(ItemCondition.parseKey(null)).isEmpty();
    }

    @Test
    void fromKeyFallsBackToUsedGoodSoReadsNeverBlowUp() {
        // 읽기 경로는 관대해야 한다. 저장값 하나 때문에 매물 조회 전체가 실패하면 안 된다.
        assertThat(ItemCondition.fromKey("nonsense")).isEqualTo(ItemCondition.USED_GOOD);
        assertThat(ItemCondition.fromKey(null)).isEqualTo(ItemCondition.USED_GOOD);
        assertThat(ItemCondition.fromKey("used_complete")).isEqualTo(ItemCondition.USED_GOOD);
    }

    @Test
    void storageNamesIncludeLegacyNamesSoFiltersDoNotLoseOldListings() {
        assertThat(ItemCondition.USED_GOOD.storageNames()).containsExactlyInAnyOrder("USED_GOOD", "USED_COMPLETE");
        assertThat(ItemCondition.USED_FAIR.storageNames()).containsExactlyInAnyOrder("USED_FAIR", "USED_INCOMPLETE");
        assertThat(ItemCondition.NEW_SEALED.storageNames()).containsExactly("NEW_SEALED");
        assertThat(ItemCondition.LIKE_NEW.storageNames()).containsExactly("LIKE_NEW");
        assertThat(ItemCondition.DAMAGED.storageNames()).containsExactly("DAMAGED");
    }

    @Test
    void everyGradeRoundTripsThroughItsKey() {
        for (ItemCondition condition : ItemCondition.values()) {
            assertThat(ItemCondition.parseKey(condition.key())).contains(condition);
        }
    }
}
