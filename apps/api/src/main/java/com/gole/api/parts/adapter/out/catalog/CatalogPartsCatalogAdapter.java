package com.gole.api.parts.adapter.out.catalog;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.parts.application.port.out.PartsCatalogPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 컨텍스트 통합 어댑터. {@link PartsCatalogPort}를 catalog의 인바운드 유스케이스로 위임한다.
 *
 * <p>catalog는 없는 세트를 404 예외로 알린다. 그 예외를 "없음"으로 환원해 부품 요청 쪽 오류 코드
 * ({@code PART_REQUEST_SET_NOT_FOUND})는 서비스가 정하게 한다. 조회 장애처럼 404가 아닌 실패는 그대로
 * 올려 보낸다 — 있는 세트를 없다고 거절하면 안 된다.
 */
@Component
public class CatalogPartsCatalogAdapter implements PartsCatalogPort {

    private final FindLegoSetUseCase findLegoSet;

    public CatalogPartsCatalogAdapter(FindLegoSetUseCase findLegoSet) {
        this.findLegoSet = findLegoSet;
    }

    @Override
    public Optional<String> setName(String setNumber) {
        try {
            LegoSet set = findLegoSet.findBySetNumber(setNumber);
            if (set == null) {
                return Optional.empty();
            }
            String name = set.getName();
            return Optional.of(name == null || name.isBlank() ? setNumber : name);
        } catch (NotFoundException notFound) {
            return Optional.empty();
        }
    }
}
