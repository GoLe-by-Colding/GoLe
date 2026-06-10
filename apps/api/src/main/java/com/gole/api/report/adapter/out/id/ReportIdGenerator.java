package com.gole.api.report.adapter.out.id;

import com.gole.api.report.application.port.out.ReportIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 신고 식별자 생성 어댑터.
 */
@Component
public class ReportIdGenerator implements ReportIdGeneratorPort {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
