package com.gole.api.parts.application.port.in;

import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import java.util.List;
import java.util.Locale;

/** Inbound port: 부품 요청 게시판·내 요청 목록. (wanted-parts W3, W4) */
public interface ListPartRequestsUseCase {

    int DEFAULT_LIMIT = 20;
    int MAX_LIMIT = 50;
    int MINE_LIMIT = 50;

    /** 공개 게시판. 최신순. */
    List<PartRequest> list(ListPartRequestsQuery query);

    /** 내 요청. 상태 무관, 최신순 최대 {@value #MINE_LIMIT}건. */
    List<PartRequest> listMine(String requesterId);

    /**
     * 게시판 조회 조건. 상한을 넘거나 1보다 작은 limit은 거절하지 않고 범위 안으로 당긴다.
     *
     * @param setNumber 세트 번호 필터(선택, 공백이면 전체)
     * @param status    상태 필터(기본 {@link StatusFilter#OPEN})
     * @param limit     1~{@value #MAX_LIMIT}, 기본 {@value #DEFAULT_LIMIT}
     */
    record ListPartRequestsQuery(String setNumber, StatusFilter status, int limit) {

        public ListPartRequestsQuery {
            setNumber = setNumber == null || setNumber.isBlank() ? null : setNumber.trim();
            status = status == null ? StatusFilter.OPEN : status;
            limit = Math.max(1, Math.min(limit, MAX_LIMIT));
        }

        public static ListPartRequestsQuery of(String setNumber, StatusFilter status, Integer limit) {
            return new ListPartRequestsQuery(setNumber, status, limit == null ? DEFAULT_LIMIT : limit);
        }
    }

    /** 게시판 상태 필터. 쿼리스트링 키는 소문자 {@code open|closed|all}. */
    enum StatusFilter {
        OPEN,
        CLOSED,
        ALL;

        /** 비어 있으면 기본값 {@link #OPEN}. 모르는 키는 400. */
        public static StatusFilter fromKey(String key) {
            if (key == null || key.isBlank()) {
                return OPEN;
            }
            try {
                return valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidPartRequestException("status는 open, closed, all 중 하나여야 합니다");
            }
        }

        /** 저장소 조회용. {@link #ALL}이면 {@code null}(상태 무관). */
        public PartRequestStatus toStatus() {
            return switch (this) {
                case OPEN -> PartRequestStatus.OPEN;
                case CLOSED -> PartRequestStatus.CLOSED;
                case ALL -> null;
            };
        }
    }
}
