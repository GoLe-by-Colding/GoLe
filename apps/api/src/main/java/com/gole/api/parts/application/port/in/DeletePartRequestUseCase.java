package com.gole.api.parts.application.port.in;

/** Inbound port: 작성자가 요청을 지운다. (wanted-parts W7) */
public interface DeletePartRequestUseCase {

    /** 없으면 404, 작성자가 아니면 403. */
    void delete(String requestId, String actorId);
}
