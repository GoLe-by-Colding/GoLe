package com.gole.api.listing.application.port.out;

import com.gole.api.listing.domain.model.InterestTag;
import java.util.List;

/**
 * Outbound port: 관심 테마에 속한 카탈로그 세트 번호.
 *
 * <p>검색어가 테마 이름("스타워즈")일 때 판매자가 관심 테마를 달지 않은 매물도 그 테마 세트 번호로 찾기 위해 쓴다.
 */
public interface CatalogThemeSetsPort {

    /** 그 테마의 세트 번호. 찾지 못하거나 카탈로그를 읽지 못하면 빈 목록 — 검색은 관심 테마·글자로 계속한다. */
    List<String> setNumbersInTheme(InterestTag theme);
}
