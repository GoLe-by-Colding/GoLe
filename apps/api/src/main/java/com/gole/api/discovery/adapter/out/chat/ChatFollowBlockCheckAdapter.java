package com.gole.api.discovery.adapter.out.chat;

import com.gole.api.chat.application.port.in.BlockRelationQueryUseCase;
import com.gole.api.discovery.application.port.out.FollowBlockCheckPort;
import org.springframework.stereotype.Component;

/** chat 차단 관계 조회를 팔로우 차단 검사 포트에 연결한다. */
@Component
public class ChatFollowBlockCheckAdapter implements FollowBlockCheckPort {

    private final BlockRelationQueryUseCase blocks;

    public ChatFollowBlockCheckAdapter(BlockRelationQueryUseCase blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean blockedBetween(String a, String b) {
        return blocks.blockedBetween(a, b);
    }
}
