package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.PseudonymizeAdminActionTargetsUseCase;
import com.gole.api.admin.application.port.out.AdminAuditPseudonymizationPort;
import com.gole.api.admin.domain.model.AdminTargetType;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 개인정보 파기에 따른 감사 기록 대상 ID 가명화. */
@Service
public class AdminAuditPseudonymizationService implements PseudonymizeAdminActionTargetsUseCase {

    private final AdminAuditPseudonymizationPort pseudonymization;

    public AdminAuditPseudonymizationService(AdminAuditPseudonymizationPort pseudonymization) {
        this.pseudonymization = pseudonymization;
    }

    @Override
    public long replaceTargetId(AdminTargetType targetType, String fromTargetId, String toTargetId) {
        Objects.requireNonNull(targetType, "targetType");
        if (fromTargetId == null || fromTargetId.isBlank() || toTargetId == null || toTargetId.isBlank()) {
            throw new IllegalArgumentException("target ids must not be blank");
        }
        return pseudonymization.replaceTargetId(targetType, fromTargetId, toTargetId);
    }
}
