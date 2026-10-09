package com.gole.api.parts.adapter.in.web;

import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.WantedPart;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 부품 요청 API 요청·응답 모델. (wanted-parts W1, W9)
 *
 * <p>요청 본문에 Bean Validation을 걸지 않는다. 규칙은 도메인이 한 곳에서 검사하고 위반은 모두
 * {@code PART_REQUEST_INVALID}(400)로 나간다 — 여기서 먼저 막으면 같은 위반이 {@code VALIDATION_ERROR}로
 * 갈라져 프론트가 코드 두 개를 다뤄야 한다.
 */
public final class PartRequestDtos {

    private PartRequestDtos() {}

    /**
     * @param setNumber 카탈로그 세트 번호(선택)
     * @param items     찾는 부품 1~20개
     * @param note      메모(선택, 500자 이하)
     */
    public record CreatePartRequestRequest(String setNumber, List<WantedPartRequest> items, String note) {}

    public record WantedPartRequest(String partNumber, String colorName, Integer quantity) {}

    /** status는 소문자 {@code open|closed}. setNumber·closedAt은 없으면 {@code null}. */
    public record PartRequestResponse(
            String id,
            String requesterId,
            String setNumber,
            List<WantedPartResponse> items,
            String note,
            String status,
            Instant createdAt,
            Instant closedAt) {

        public static PartRequestResponse from(PartRequest request) {
            return new PartRequestResponse(
                    request.getId(),
                    request.getRequesterId(),
                    request.getSetNumber(),
                    request.getItems().stream().map(WantedPartResponse::from).toList(),
                    request.getNote(),
                    request.getStatus().name().toLowerCase(Locale.ROOT),
                    request.getCreatedAt(),
                    request.getClosedAt());
        }
    }

    public record WantedPartResponse(String partNumber, String colorName, int quantity) {

        static WantedPartResponse from(WantedPart part) {
            return new WantedPartResponse(part.partNumber(), part.colorName(), part.quantity());
        }
    }
}
