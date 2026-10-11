package com.gole.api.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.out.AdminAuditPseudonymizationPort;
import com.gole.api.admin.domain.model.AdminTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAuditPseudonymizationServiceTest {

    private final AdminAuditPseudonymizationPort port = mock(AdminAuditPseudonymizationPort.class);
    private final AdminAuditPseudonymizationService service = new AdminAuditPseudonymizationService(port);

    @Test
    @DisplayName("대상 ID를 영수증 ID로 바꾸고 바꾼 건수를 돌려준다")
    void replaceTargetId_delegates() {
        when(port.replaceTargetId(AdminTargetType.SUPPORT_TICKET, "room-1", "receipt-1"))
                .thenReturn(2L);

        assertThat(service.replaceTargetId(AdminTargetType.SUPPORT_TICKET, "room-1", "receipt-1"))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("빈 ID로는 감사 기록을 건드리지 않는다")
    void replaceTargetId_rejectsBlankIds() {
        assertThatThrownBy(() -> service.replaceTargetId(AdminTargetType.SUPPORT_TICKET, " ", "receipt-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceTargetId(AdminTargetType.SUPPORT_TICKET, "room-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port);
    }
}
