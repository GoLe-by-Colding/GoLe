package com.gole.api.admin.application.port.in;

import com.gole.api.admin.domain.model.AdminListingRow;
import com.gole.api.admin.domain.model.AdminOrderRow;
import com.gole.api.admin.domain.model.AdminOrderStats;
import com.gole.api.admin.domain.model.AdminPostRow;
import com.gole.api.admin.domain.model.AdminVolumeCounts;
import java.util.List;

/** Inbound port: 운영 화면용 읽기 전용 조회(대시보드 집계·주문/매물/게시글 모니터링). (admin-console 요구사항 9.2) */
public interface QueryAdminReadModelUseCase {

    AdminVolumeCounts volumeCounts();

    AdminOrderStats orderStats();

    long activeListingCount();

    List<AdminOrderRow> recentOrders(String status, String query, int limit);

    List<AdminListingRow> recentListings(String status, String query, int limit);

    List<AdminPostRow> recentPosts(String status, String query, int limit);
}
