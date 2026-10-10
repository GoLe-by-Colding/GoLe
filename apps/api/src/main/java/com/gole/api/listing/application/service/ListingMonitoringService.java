package com.gole.api.listing.application.service;

import com.gole.api.listing.application.port.in.MonitorListingsUseCase;
import com.gole.api.listing.application.port.out.ListingMonitoringPort;
import java.util.List;
import org.springframework.stereotype.Service;

/** 운영 화면용 매물 현황. 집계와 검색은 저장소 포트가 한다. */
@Service
public class ListingMonitoringService implements MonitorListingsUseCase {

    private final ListingMonitoringPort monitoring;

    public ListingMonitoringService(ListingMonitoringPort monitoring) {
        this.monitoring = monitoring;
    }

    @Override
    public long activeListingCount() {
        return monitoring.activeListingCount();
    }

    @Override
    public List<ListingMonitorRow> recentListings(String status, String query, int limit) {
        return monitoring.recentListings(status, query, limit);
    }

    @Override
    public long estimatedListingCount() {
        return monitoring.estimatedListingCount();
    }
}
