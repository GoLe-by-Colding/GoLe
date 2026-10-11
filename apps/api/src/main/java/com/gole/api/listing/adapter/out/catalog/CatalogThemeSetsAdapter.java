package com.gole.api.listing.adapter.out.catalog;

import com.gole.api.catalog.application.port.in.FindThemeSetNumbersUseCase;
import com.gole.api.listing.application.port.out.CatalogThemeSetsPort;
import com.gole.api.listing.domain.model.InterestTag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link CatalogThemeSetsPort}를 catalog의 인바운드 포트로 위임한다. 관심 테마 키("star-wars")를 테마 이름 낱말로 넘기고,
 * catalog가 영어 테마 이름("Star Wars")과 맞춘다. listing은 catalog 도메인 객체를 받지 않고 세트 번호만 받는다.
 */
@Component
public class CatalogThemeSetsAdapter implements CatalogThemeSetsPort {

    /** 한 테마에서 검색 조건에 넣는 세트 번호 상한. 이보다 큰 테마는 관심 테마를 단 매물·글자 검색에 기댄다. */
    static final int MAX_THEME_SETS = 300;

    private static final Logger log = LoggerFactory.getLogger(CatalogThemeSetsAdapter.class);

    private final FindThemeSetNumbersUseCase catalog;

    public CatalogThemeSetsAdapter(FindThemeSetNumbersUseCase catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<String> setNumbersInTheme(InterestTag theme) {
        try {
            return catalog.setNumbersInTheme(theme.key(), MAX_THEME_SETS);
        } catch (RuntimeException e) {
            // 카탈로그를 못 읽어도 검색 자체는 실패시키지 않는다 — 관심 테마·글자로만 찾는다.
            log.warn("[listing-search] 테마 세트 번호를 읽지 못해 관심 테마·글자로만 찾음: theme={}", theme.key(), e);
            return List.of();
        }
    }
}
