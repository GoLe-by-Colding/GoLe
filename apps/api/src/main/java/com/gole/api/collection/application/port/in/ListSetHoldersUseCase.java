package com.gole.api.collection.application.port.in;

import com.gole.api.collection.domain.model.OwnershipStatus;
import java.util.List;

/** 다른 컨텍스트가 특정 세트를 보유·희망 등록한 사용자에게 소식을 전달할 때 쓰는 조회 포트. */
public interface ListSetHoldersUseCase {

    List<String> holdersOf(String setNumber, OwnershipStatus status);
}
