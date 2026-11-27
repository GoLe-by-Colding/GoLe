package com.gole.api.community.adapter.out.chat;

import com.gole.api.chat.application.port.in.BlockRelationQueryUseCase;
import com.gole.api.community.application.port.out.BlockedAuthorQueryPort;
import java.util.List;
import org.springframework.stereotype.Component;

/** chat 컨텍스트의 차단 관계 조회를 커뮤니티 포트에 연결한다. */
@Component
public class ChatBlockedAuthorQueryAdapter implements BlockedAuthorQueryPort {

    private final BlockRelationQueryUseCase blocks;

    public ChatBlockedAuthorQueryAdapter(BlockRelationQueryUseCase blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean blockedBetween(String a, String b) {
        return blocks.blockedBetween(a, b);
    }

    @Override
    public List<String> blockedCounterparts(String accountId) {
        return blocks.blockedCounterparts(accountId);
    }
}
