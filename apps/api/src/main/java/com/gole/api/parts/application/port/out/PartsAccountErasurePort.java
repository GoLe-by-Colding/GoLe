package com.gole.api.parts.application.port.out;

import com.gole.api.parts.application.port.in.PartsAccountErasureUseCase.PartsErasure;

/** Outbound port: 회원 탈퇴 때 부품 요청 기록의 차단 판정·파기를 저장소에서 한다. */
public interface PartsAccountErasurePort {

    PartsErasure erase(String accountId, String anonymousSubject);
}
