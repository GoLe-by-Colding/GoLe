package com.gole.api.catalog.application.port.in;

import java.util.List;

/**
 * Inbound port: 한 테마에 속한 카탈로그 세트 번호. 다른 컨텍스트가 테마로 묶어 찾을 때 쓴다(listing 검색의 "스타워즈").
 */
public interface FindThemeSetNumbersUseCase {

    /**
     * 테마 이름이 {@code themeSlug}("star-wars")의 낱말을 차례로 담은 세트의 번호. 대소문자·띄어쓰기·하이픈·밑줄은 가리지 않고
     * 이름 일부로 맞춘다("super-heroes" → "Marvel Super Heroes"). 낱말이 없거나 영숫자가 아니면 빈 목록이다. 최대 {@code limit}개.
     */
    List<String> setNumbersInTheme(String themeSlug, int limit);
}
