package com.gole.api.catalog.application.port.out;

import com.gole.api.catalog.domain.model.RetirementStatus;

/** 세트 단종 상태가 바뀐 뒤 관심·보유 사용자에게 소식을 전달하는 포트. 실패는 구현이 흡수한다. */
public interface SetRetirementNotifierPort {

    void retirementChanged(String setNumber, String setName, RetirementStatus status);
}
