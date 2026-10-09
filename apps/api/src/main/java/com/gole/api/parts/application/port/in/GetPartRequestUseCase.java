package com.gole.api.parts.application.port.in;

import com.gole.api.parts.domain.model.PartRequest;

/** Inbound port: 부품 요청 단건 조회(공개). (wanted-parts W5) */
public interface GetPartRequestUseCase {

    /** @throws com.gole.api.parts.domain.exception.PartRequestNotFoundException 없으면 404 */
    PartRequest get(String requestId);
}
