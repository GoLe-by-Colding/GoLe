package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.domain.model.ChatBlock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    /** 호출 횟수를 세는 가짜 저장소. 입력 검증이 저장소 앞에서 끊기는지 확인한다. */
    private static final class FakeChatBlocks implements ChatBlockRepositoryPort {

        private final List<ChatBlock> stored = new ArrayList<>();
        private int calls;

        @Override
        public void save(ChatBlock block) {
            calls++;
            stored.add(block);
        }

        @Override
        public void delete(String blockerId, String blockedId) {
            calls++;
        }

        @Override
        public boolean blockedBetween(String a, String b) {
            calls++;
            return stored.stream().anyMatch(block -> sameUnordered(block, a, b));
        }

        @Override
        public boolean blockedBetweenAny(Collection<String> leftAccountIds, Collection<String> rightAccountIds) {
            calls++;
            return false;
        }

        @Override
        public List<String> blockedCounterparts(String accountId) {
            calls++;
            return stored.stream()
                    .filter(block -> block.blockerId().equals(accountId)
                            || block.blockedId().equals(accountId))
                    .map(block -> block.blockerId().equals(accountId) ? block.blockedId() : block.blockerId())
                    .distinct()
                    .toList();
        }

        @Override
        public List<String> blockedTargets(String blockerId) {
            calls++;
            return stored.stream()
                    .filter(block -> block.blockerId().equals(blockerId))
                    .map(ChatBlock::blockedId)
                    .toList();
        }

        private static boolean sameUnordered(ChatBlock block, String a, String b) {
            return (block.blockerId().equals(a) && block.blockedId().equals(b))
                    || (block.blockerId().equals(b) && block.blockedId().equals(a));
        }
    }
}
