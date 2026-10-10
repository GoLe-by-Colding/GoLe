package com.gole.api.chat.adapter.out.admin;

import com.gole.api.admin.application.port.in.PseudonymizeAdminActionTargetsUseCase;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.port.out.SupportAuditReferencePort;
import org.springframework.stereotype.Component;

/** 관리자 컨텍스트 통합 어댑터. 문의 대화 파기 때 감사 기록 가명화를 admin 의 인바운드 포트에 맡긴다. */
@Component
public class AdminSupportAuditReferenceAdapter implements SupportAuditReferencePort {

    private final PseudonymizeAdminActionTargetsUseCase pseudonymize;

    public AdminSupportAuditReferenceAdapter(PseudonymizeAdminActionTargetsUseCase pseudonymize) {
        this.pseudonymize = pseudonymize;
    }

    @Override
    public long pseudonymizeSupportTicketReferences(String roomId, String receiptId) {
        return pseudonymize.replaceTargetId(AdminTargetType.SUPPORT_TICKET, roomId, receiptId);
    }
}
