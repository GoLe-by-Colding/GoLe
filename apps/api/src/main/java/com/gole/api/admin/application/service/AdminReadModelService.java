package com.gole.api.admin.application.service;

import com.gole.api.admin.application.port.in.QueryAdminReadModelUseCase;
import com.gole.api.admin.application.port.out.AdminReadModelPort;
import com.gole.api.admin.domain.model.AdminListingRow;
import com.gole.api.admin.domain.model.AdminOrderRow;
import com.gole.api.admin.domain.model.AdminOrderStats;
import com.gole.api.admin.domain.model.AdminPostRow;
import com.gole.api.admin.domain.model.AdminVolumeCounts;
import java.util.List;
import org.springframework.stereotype.Service;

/** 운영 화면 읽기 모델 조회. 컨트롤러가 저장소 포트를 직접 알지 않게 하는 얇은 유스케이스다. */
@Service
public class AdminReadModelService implements QueryAdminReadModelUseCase {

    private final AdminReadModelPort readModel;

    public AdminReadModelService(AdminReadModelPort readModel) {
        this.readModel = readModel;
    }

    @Override
    public AdminVolumeCounts volumeCounts() {
        return readModel.volumeCounts();
    }

    @Override
    public AdminOrderStats orderStats() {
        return readModel.orderStats();
    }

    @Override
    public long activeListingCount() {
        return readModel.activeListingCount();
    }

    @Override
    public List<AdminOrderRow> recentOrders(String status, String query, int limit) {
        return readModel.recentOrders(status, query, limit);
    }

    @Override
    public List<AdminListingRow> recentListings(String status, String query, int limit) {
        return readModel.recentListings(status, query, limit);
    }

    @Override
    public List<AdminPostRow> recentPosts(String status, String query, int limit) {
        return readModel.recentPosts(status, query, limit);
    }
}
