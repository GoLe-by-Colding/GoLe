package com.gole.api.bid.adapter.out.catalog;

import com.gole.api.bid.application.port.out.BidCatalogPort;
import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.common.exception.NotFoundException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 컨텍스트 통합 어댑터. {@link BidCatalogPort}를 catalog의 {@link FindLegoSetUseCase}로 위임한다.
 *
 * <p>catalog의 404를 "없음"으로 환원해 오류 코드({@code BID_SET_NOT_FOUND})는 서비스가 정하게 한다. 404가 아닌
 * 실패는 그대로 올린다 — 있는 세트를 없다고 거절하면 안 된다({@code CatalogPartsCatalogAdapter}와 같은 규칙).
 */
@Component
public class CatalogBidCatalogAdapter implements BidCatalogPort {

    private final FindLegoSetUseCase findLegoSet;

    public CatalogBidCatalogAdapter(FindLegoSetUseCase findLegoSet) {
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
