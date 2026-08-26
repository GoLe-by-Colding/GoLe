package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.application.port.out.AdminReadModelPort.OrderStats;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import com.gole.api.order.application.port.in.ManageSettlementsUseCase;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminDashboardControllerTest {

    @Test
    @DisplayName("관리자 대시보드 집계에 비밀값 없는 결제 준비 상태를 포함한다")
    void includesPaymentReadinessInOverview() {
        AdminReadModelPort readModel = mock(AdminReadModelPort.class);
        when(readModel.collectionCounts(anyList())).thenReturn(Map.of("orders", 2L));
        when(readModel.orderStats()).thenReturn(new OrderStats(Map.of("PAYMENT_PENDING", 2L), 0));
        GetPaymentReadinessUseCase paymentReadiness = mock(GetPaymentReadinessUseCase.class);
        when(paymentReadiness.getPaymentReadiness())
                .thenReturn(new Snapshot(
                        true, true, State.READY, ChannelType.TEST, List.of("KAKAOPAY", "CARD"), "KRW", List.of()));
        AdminDashboardController controller = new AdminDashboardController(
                readModel,
                mock(ListAdminActionsUseCase.class),
                mock(ManageReportsUseCase.class),
                mock(ManageSettlementsUseCase.class),
                paymentReadiness);

        var overview = controller.overview();

        assertThat(overview.paymentReadiness().enabled()).isTrue();
        assertThat(overview.paymentReadiness().ready()).isTrue();
        assertThat(overview.paymentReadiness().state()).isEqualTo("READY");
        assertThat(overview.paymentReadiness().channelType()).isEqualTo("TEST");
        assertThat(overview.paymentReadiness().methods()).containsExactly("KAKAOPAY", "CARD");
        assertThat(overview.paymentReadiness().currency()).isEqualTo("KRW");
        assertThat(overview.paymentReadiness().issues()).isEmpty();
    }
}
