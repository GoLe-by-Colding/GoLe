package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.domain.model.ChatBlock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 호출 횟수를 세는 가짜 저장소. 입력 검증이 저장소 앞에서 끊기는지 확인한다. */
final class FakeChatBlocks implements ChatBlockRepositoryPort {

    private final List<ChatBlock> stored = new ArrayList<>();
    int calls;

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
                .filter(block ->
                        block.blockerId().equals(accountId) || block.blockedId().equals(accountId))
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
