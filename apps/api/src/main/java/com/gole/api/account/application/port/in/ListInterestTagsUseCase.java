package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.InterestTag;
import java.util.List;

/**
 * Inbound port: 선택 가능한 관심 태그 목록. (onboarding R6, D8)
 *
 * <p>DB 컬렉션이 아니라 curated 상수를 노출한다 — 화면이 하드코딩한 목록과 서버 검증 목록이
 * 갈라지지 않게 하는 것이 이 엔드포인트의 존재 이유다.
 */
public interface ListInterestTagsUseCase {

    List<InterestTag> availableTags();
}
