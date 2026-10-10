package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.MonitorOrdersUseCase;
import com.gole.api.order.application.port.out.OrderMonitoringPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 운영 화면용 주문 현황. 집계와 검색은 저장소 포트가 한다. */
@Service
public class OrderMonitoringService implements MonitorOrdersUseCase {

    private final OrderMonitoringPort monitoring;

    public OrderMonitoringService(OrderMonitoringPort monitoring) {
        this.monitoring = monitoring;
    }

    @Override
    public OrderStats orderStats() {
        return monitoring.orderStats();
    }

    @Override
    public List<OrderMonitorRow> recentOrders(String status, String query, int limit) {
        return monitoring.recentOrders(status, query, limit);
    }

    @Override
    public long estimatedOrderCount() {
        return monitoring.estimatedOrderCount();
    }
}
