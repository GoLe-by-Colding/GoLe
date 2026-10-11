package com.gole.api.pricing.adapter.out.catalog;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.pricing.application.port.out.TrendingSetCatalogPort;
import org.springframework.stereotype.Component;

/** 카탈로그 컨텍스트 통합 어댑터. 세트 도메인 객체에서 인기 목록에 필요한 이름·이미지만 꺼낸다. */
@Component
public class CatalogTrendingSetAdapter implements TrendingSetCatalogPort {

    private final FindLegoSetUseCase findLegoSet;

    public CatalogTrendingSetAdapter(FindLegoSetUseCase findLegoSet) {
        this.findLegoSet = findLegoSet;
    }

    @Override
    public SetLabel labelOf(String setNumber) {
        LegoSet set = findLegoSet.findBySetNumber(setNumber);
        return new SetLabel(set.getName(), set.getImageUrl());
    }
}
