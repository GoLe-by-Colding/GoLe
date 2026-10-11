package com.gole.api.listing.application.port.out;

import com.gole.api.listing.application.port.in.MonitorListingsUseCase.ListingMonitorRow;
import java.util.List;

/** Outbound port: 운영 화면용 매물 집계·목록 조회. */
public interface ListingMonitoringPort {

    long activeListingCount();

    List<ListingMonitorRow> recentListings(String status, String query, int limit);

    long estimatedListingCount();
}
