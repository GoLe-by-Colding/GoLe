package com.gole.api.shipping.application.port.out;

/** 인증 정보·제공자 응답·원본 운송장 번호가 이 경계를 넘어가지 않도록 한다. */
public interface TrackerAdminAuditPort {
    void record(String actorId, String action, String targetId);
}
