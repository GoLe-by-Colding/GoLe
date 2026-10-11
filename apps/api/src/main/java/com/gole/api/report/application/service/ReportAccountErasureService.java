package com.gole.api.report.application.service;

import com.gole.api.report.application.port.in.ReportAccountErasureUseCase;
import com.gole.api.report.application.port.out.ReportAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 신고 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class ReportAccountErasureService implements ReportAccountErasureUseCase {

    private final ReportAccountErasurePort erasure;

    public ReportAccountErasureService(ReportAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasPendingReport(String accountId) {
        return erasure.hasPendingReport(accountId);
    }

    @Override
    public ReportErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
