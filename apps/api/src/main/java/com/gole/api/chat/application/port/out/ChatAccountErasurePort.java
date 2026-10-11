package com.gole.api.chat.application.port.out;

import com.gole.api.chat.application.port.in.ChatAccountErasureUseCase.ChatErasure;

/** Outbound port: 회원 탈퇴 때 채팅 기록의 차단 판정·파기를 저장소에서 한다. */
public interface ChatAccountErasurePort {

    boolean hasSupportRecords(String accountId);

    boolean ownsOpenGroup(String accountId);

    ChatErasure erase(String accountId, String anonymousSubject);
}
