package com.gole.api.parts.domain.model;

/** 부품 요청 상태. 마감은 작성자만 하고 되돌리지 않는다. (wanted-parts W6) */
public enum PartRequestStatus {
    OPEN,
    CLOSED
}
