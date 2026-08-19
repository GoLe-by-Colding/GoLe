package com.gole.api.shipping.application.port.out;

import com.gole.api.shipping.application.port.out.DeliveryTrackerPort.TrackingResult;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.time.Duration;
import java.util.Optional;

/**
 * Outbound port: 트래커 응답 캐시. (R2.5)
 * 외부 API 호출량을 제한한다. 캐시 장애는 미스로 취급한다.
 */
public interface TrackerCachePort {

    Optional<TrackingResult> get(Carrier carrier, WaybillNumber waybill);

    void put(Carrier carrier, WaybillNumber waybill, TrackingResult result, Duration ttl);
}
