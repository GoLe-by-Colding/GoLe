package com.gole.api.parts.application.port.in;

import com.gole.api.parts.domain.model.PartRequest;

/** Inbound port: 작성자가 요청을 마감한다. (wanted-parts W6) */
public interface ClosePartRequestUseCase {

    /** 마감된 요청을 돌려준다. 없으면 404, 작성자가 아니면 403, 이미 마감됐으면 409. */
    PartRequest close(String requestId, String actorId);
}
