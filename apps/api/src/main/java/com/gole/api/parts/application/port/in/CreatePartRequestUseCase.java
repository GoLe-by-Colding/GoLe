package com.gole.api.parts.application.port.in;

import com.gole.api.parts.domain.model.PartRequest;
import java.util.List;

/** Inbound port: 부족 부품 요청 게시. (wanted-parts W1, W2, W8) */
public interface CreatePartRequestUseCase {

    /**
     * 요청을 만들고, 세트가 지정됐으면 그 세트 보유자에게 알린다(best-effort).
     *
     * @throws com.gole.api.parts.domain.exception.InvalidPartRequestException 입력 규칙 위반(400)
     */
    PartRequest create(CreatePartRequestCommand command);

    /**
     * @param requesterId 세션에서 정한 작성자 id
     * @param setNumber   카탈로그 세트 번호(선택, 비어 있으면 세트 없는 요청)
     * @param items       찾는 부품 1~20개
     * @param note        메모(선택, 500자 이하)
     */
    record CreatePartRequestCommand(String requesterId, String setNumber, List<ItemCommand> items, String note) {}

    /** 요청 본문 그대로의 부품 한 줄. 수량이 비어 올 수 있어 박싱 타입이다. */
    record ItemCommand(String partNumber, String colorName, Integer quantity) {}
}
