package com.gole.api.collection.application.port.out;

import com.gole.api.collection.application.port.in.CollectionAccountErasureUseCase.CollectionErasure;

/** Outbound port: 회원 탈퇴 때 컬렉션 기록의 차단 판정·파기를 저장소에서 한다. */
public interface CollectionAccountErasurePort {

    CollectionErasure erase(String accountId, String anonymousSubject);
}
