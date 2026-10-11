package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.in.ListAdminActionsUseCase;
import com.gole.api.admin.application.port.in.QueryAdminReadModelUseCase;
import com.gole.api.admin.domain.model.AdminOrderStats;
import com.gole.api.admin.domain.model.AdminVolumeCounts;
import com.gole.api.chat.application.port.in.SupportConsoleUseCase;
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
    @DisplayName("관리자 대시보드 집계에 미배정 문의 수와 비밀값 없는 결제 준비 상태를 포함한다")
    void includesPaymentReadinessInOverview() {
        QueryAdminReadModelUseCase readModel = mock(QueryAdminReadModelUseCase.class);
        when(readModel.volumeCounts()).thenReturn(new AdminVolumeCounts(1, 2, 3, 4, 5, 6, 7));
        when(readModel.orderStats()).thenReturn(new AdminOrderStats(Map.of("PAYMENT_PENDING", 2L), 0));
        GetPaymentReadinessUseCase paymentReadiness = mock(GetPaymentReadinessUseCase.class);
        when(paymentReadiness.getPaymentReadiness())
                .thenReturn(new Snapshot(
                        true, true, State.READY, ChannelType.TEST, List.of("KAKAOPAY", "CARD"), "KRW", List.of()));
        SupportConsoleUseCase support = mock(SupportConsoleUseCase.class);
        when(support.countUnassigned()).thenReturn(4L);
        AdminDashboardController controller = new AdminDashboardController(
                readModel,
                mock(ListAdminActionsUseCase.class),
                mock(ManageReportsUseCase.class),
                support,
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
        assertThat(overview.unassignedSupportTickets()).isEqualTo(4);
    }

    @Test
    @DisplayName("규모 숫자는 화면 라벨이 쓰는 키와 순서 그대로 응답한다")
    void overviewKeepsCountKeysAndOrderForTheConsole() {
        QueryAdminReadModelUseCase readModel = mock(QueryAdminReadModelUseCase.class);
        when(readModel.volumeCounts()).thenReturn(new AdminVolumeCounts(1, 2, 3, 4, 5, 6, 7));
        when(readModel.orderStats()).thenReturn(new AdminOrderStats(Map.of(), 0));
        GetPaymentReadinessUseCase paymentReadiness = mock(GetPaymentReadinessUseCase.class);
        when(paymentReadiness.getPaymentReadiness())
                .thenReturn(new Snapshot(false, false, State.READY, ChannelType.TEST, List.of(), "KRW", List.of()));
        AdminDashboardController controller = new AdminDashboardController(
                readModel,
                mock(ListAdminActionsUseCase.class),
                mock(ManageReportsUseCase.class),
                mock(SupportConsoleUseCase.class),
                mock(ManageSettlementsUseCase.class),
                paymentReadiness);

        assertThat(controller.overview().counts())
                .containsExactly(
                        Map.entry("accounts", 1L),
                        Map.entry("lego_sets", 2L),
                        Map.entry("listings", 3L),
                        Map.entry("orders", 4L),
                        Map.entry("posts", 5L),
                        Map.entry("reviews", 6L),
                        Map.entry("price_transactions", 7L));
    }
}
