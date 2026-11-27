package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.BlockRelationQueryUseCase;
import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 차단 관계 조회. 입력이 비면 저장소를 부르지 않고 거짓·빈 목록을 돌려준다. */
@Service
public class BlockRelationQueryService implements BlockRelationQueryUseCase {

    private final ChatBlockRepositoryPort blocks;

    public BlockRelationQueryService(ChatBlockRepositoryPort blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean blockedBetween(String a, String b) {
        if (isBlank(a) || isBlank(b)) {
            return false;
        }
        return blocks.blockedBetween(a, b);
    }

    @Override
    public List<String> blockedCounterparts(String accountId) {
        if (isBlank(accountId)) {
            return List.of();
        }
        return blocks.blockedCounterparts(accountId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
