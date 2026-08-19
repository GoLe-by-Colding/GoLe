package com.gole.api.shipping.domain.model;

import java.time.Instant;

/** 운송장 교체 이력 항목. 직전 운송장이 무엇이었는지 보존한다. (R1.4) */
public record WaybillChange(Carrier carrier, String waybillNumber, Instant replacedAt) {}
