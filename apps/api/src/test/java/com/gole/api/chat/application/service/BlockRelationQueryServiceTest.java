package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.domain.model.ChatBlock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockRelationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final FakeChatBlocks blocks = new FakeChatBlocks();
    private final BlockRelationQueryService service = new BlockRelationQueryService(blocks);

    @Test
    @DisplayName("차단한 쪽과 당한 쪽 모두에게 차단 관계가 보인다")
    void blockedBetween_isDirectionless() {
        blocks.save(new ChatBlock("A", "B", null, NOW));

        assertThat(service.blockedBetween("A", "B")).isTrue();
        assertThat(service.blockedBetween("B", "A")).isTrue();
        assertThat(service.blockedBetween("A", "C")).isFalse();
    }

    @Test
    @DisplayName("차단 상대는 방향과 무관하게 중복 없이 조회된다")
    void blockedCounterparts_isBidirectional() {
        blocks.save(new ChatBlock("A", "B", null, NOW));

        assertThat(service.blockedCounterparts("B")).containsExactly("A");
        assertThat(service.blockedCounterparts("A")).containsExactly("B");
    }

    @Test
    @DisplayName("null이나 빈 입력은 저장소를 부르지 않고 거짓이나 빈 목록을 돌려준다")
    void blankInput_neverReachesStorage() {
        assertThat(service.blockedBetween(null, "B")).isFalse();
        assertThat(service.blockedBetween("A", "")).isFalse();
        assertThat(service.blockedCounterparts(null)).isEmpty();
        assertThat(service.blockedCounterparts("  ")).isEmpty();

        assertThat(blocks.calls).isZero();
    }
}
