package com.gole.api.parts.domain.model;

import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.exception.PartRequestNotOpenException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 부족 부품 요청. "이 세트 조립하는데 이 부품이 몇 개 없다"를 게시한다. (wanted-parts W1, W6, W9)
 *
 * <p>세트 번호는 선택이다. 세트 없이 부품만 찾는 요청도 받는다 — 세트가 있으면 그 세트 보유자에게 알림이
 * 가는 것만 다르다. 카탈로그에 실제로 있는 세트인지는 서비스가 카탈로그 포트로 확인한다.
 */
public final class PartRequest {

    public static final int MIN_ITEMS = 1;
    public static final int MAX_ITEMS = 20;
    public static final int MAX_NOTE_LENGTH = 500;
    public static final int MAX_SET_NUMBER_LENGTH = 30;

    private final String id;
    private final String requesterId;
    private final String setNumber;
    private final List<WantedPart> items;
    private final String note;
    private final PartRequestStatus status;
    private final Instant createdAt;
    private final Instant closedAt;

    private PartRequest(
            String id,
            String requesterId,
            String setNumber,
            List<WantedPart> items,
            String note,
            PartRequestStatus status,
            Instant createdAt,
            Instant closedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.requesterId = Objects.requireNonNull(requesterId, "requesterId");
        this.setNumber = setNumber;
        this.items = List.copyOf(items);
        this.note = note == null ? "" : note;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.closedAt = closedAt;
    }

    /** 새 요청. 입력 규칙(W1)을 여기서 모두 검사하고, 세트 번호·메모의 앞뒤 공백을 지운다. */
    public static PartRequest create(
            String id, String requesterId, String setNumber, List<WantedPart> items, String note, Instant now) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId must not be blank");
        }
        if (items == null || items.size() < MIN_ITEMS || items.size() > MAX_ITEMS) {
            throw new InvalidPartRequestException("부품은 1~20개까지 적을 수 있습니다");
        }
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPartRequestException("빈 부품 항목이 있습니다");
        }
        return new PartRequest(
                id,
                requesterId,
                normalizeSetNumber(setNumber),
                items,
                normalizeNote(note),
                PartRequestStatus.OPEN,
                now,
                null);
    }

    /** 저장소에서 읽은 값을 그대로 되살린다. 입력 규칙은 생성 때 이미 통과했으므로 다시 묻지 않는다. */
    public static PartRequest restore(
            String id,
            String requesterId,
            String setNumber,
            List<WantedPart> items,
            String note,
            PartRequestStatus status,
            Instant createdAt,
            Instant closedAt) {
        return new PartRequest(id, requesterId, setNumber, items, note, status, createdAt, closedAt);
    }

    /** 세트 번호는 선택이다. 비어 있으면 세트 없는 요청으로 본다. */
    public static String normalizeSetNumber(String setNumber) {
        if (setNumber == null || setNumber.isBlank()) {
            return null;
        }
        String trimmed = setNumber.trim();
        if (trimmed.length() > MAX_SET_NUMBER_LENGTH) {
            throw new InvalidPartRequestException("세트 번호가 너무 깁니다");
        }
        return trimmed;
    }

    private static String normalizeNote(String note) {
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new InvalidPartRequestException("메모는 500자 이하여야 합니다");
        }
        return trimmed;
    }

    /** 마감한 사본을 돌려준다. 이미 마감됐으면 409. (W6) */
    public PartRequest close(Instant now) {
        if (status != PartRequestStatus.OPEN) {
            throw new PartRequestNotOpenException(id);
        }
        return new PartRequest(id, requesterId, setNumber, items, note, PartRequestStatus.CLOSED, createdAt, now);
    }

    public boolean isRequestedBy(String accountId) {
        return requesterId.equals(accountId);
    }

    public boolean isOpen() {
        return status == PartRequestStatus.OPEN;
    }

    public boolean hasSet() {
        return setNumber != null;
    }

    public String getId() {
        return id;
    }

    public String getRequesterId() {
        return requesterId;
    }

    /** 세트 없는 요청이면 {@code null}. */
    public String getSetNumber() {
        return setNumber;
    }

    public List<WantedPart> getItems() {
        return items;
    }

    public String getNote() {
        return note;
    }

    public PartRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 열린 요청이면 {@code null}. */
    public Instant getClosedAt() {
        return closedAt;
    }
}
