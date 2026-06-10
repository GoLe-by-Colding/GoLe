package com.gole.api.catalog.application.port.in;

import com.gole.api.catalog.domain.model.RetirementStatus;

/**
 * Inbound port: 카탈로그 세트 등록(관리자). (요구사항 4)
 */
public interface CreateLegoSetUseCase {

    String create(CreateLegoSetCommand command);

    record CreateLegoSetCommand(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {}
}
