package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.ChatAccountErasureUseCase;
import com.gole.api.chat.application.port.out.ChatAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 채팅 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class ChatAccountErasureService implements ChatAccountErasureUseCase {

    private final ChatAccountErasurePort erasure;

    public ChatAccountErasureService(ChatAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasSupportRecords(String accountId) {
        return erasure.hasSupportRecords(accountId);
    }

    @Override
    public boolean ownsOpenGroup(String accountId) {
        return erasure.ownsOpenGroup(accountId);
    }

    @Override
    public ChatErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
