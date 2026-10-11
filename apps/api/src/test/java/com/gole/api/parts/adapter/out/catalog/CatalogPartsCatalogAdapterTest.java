package com.gole.api.parts.adapter.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.catalog.application.port.in.FindLegoSetUseCase;
import com.gole.api.catalog.domain.exception.LegoSetNotFoundException;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogPartsCatalogAdapterTest {

    @Test
    @DisplayName("카탈로그에 있으면 세트 이름, 404면 비어 있음")
    void setName_mapsNotFoundToEmpty() {
        FindLegoSetUseCase find = mock(FindLegoSetUseCase.class);
        when(find.findBySetNumber("10305"))
                .thenReturn(new LegoSet("10305", "Downtown Diner", "Icons", 2992, 2021, RetirementStatus.ACTIVE, null));
        when(find.findBySetNumber("99999")).thenThrow(new LegoSetNotFoundException("99999"));
        CatalogPartsCatalogAdapter adapter = new CatalogPartsCatalogAdapter(find);

        assertThat(adapter.setName("10305")).contains("Downtown Diner");
        assertThat(adapter.setName("99999")).isEmpty();
    }

    @Test
    @DisplayName("404가 아닌 조회 장애는 그대로 올린다 — 있는 세트를 없다고 거절하지 않는다")
    void setName_propagatesOtherFailures() {
        FindLegoSetUseCase find = mock(FindLegoSetUseCase.class);
        when(find.findBySetNumber("10305")).thenThrow(new IllegalStateException("mongo down"));

        assertThatThrownBy(() -> new CatalogPartsCatalogAdapter(find).setName("10305"))
                .isInstanceOf(IllegalStateException.class);
    }
}
