package com.gole.api.parts.application.port.out;

import java.util.List;

/** 특정 세트를 보유(OWNED)한 사용자 조회 outbound port. collection 컨텍스트로 구현한다. (W8) */
public interface SetOwnerQueryPort {

    List<String> ownersOf(String setNumber);
}
