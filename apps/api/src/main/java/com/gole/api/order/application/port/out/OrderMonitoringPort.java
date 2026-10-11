package com.gole.api.order.application.port.out;

import com.gole.api.order.application.port.in.MonitorOrdersUseCase.OrderMonitorRow;
import com.gole.api.order.application.port.in.MonitorOrdersUseCase.OrderStats;
import java.util.List;

/** Outbound port: 운영 화면용 주문 집계·목록 조회. */
public interface OrderMonitoringPort {

    OrderStats orderStats();

    List<OrderMonitorRow> recentOrders(String status, String query, int limit);

    long estimatedOrderCount();
}
