package com.gole.api.report.application.port.out;

import com.gole.api.report.application.port.in.ReportAccountErasureUseCase.ReportErasure;

/** Outbound port: 회원 탈퇴 때 신고 기록의 차단 판정·파기를 저장소에서 한다. */
public interface ReportAccountErasurePort {

    boolean hasPendingReport(String accountId);

    ReportErasure erase(String accountId, String anonymousSubject);
}
