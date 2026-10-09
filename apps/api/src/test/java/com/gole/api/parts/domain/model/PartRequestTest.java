package com.gole.api.parts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import com.gole.api.parts.domain.exception.PartRequestNotOpenException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final WantedPart BRICK = new WantedPart("3062b", "검정", 4);

    @ParameterizedTest
    @ValueSource(strings = {"3062b", "973pb1234c01", "3626cpr0001.1", "x-1", "A", "12345678901234567890"})
    @DisplayName("부품 번호는 영문·숫자로 시작하는 20자 이하만 받는다")
    void partNumber_acceptsCatalogStyleNumbers(String partNumber) {
        assertThat(new WantedPart(partNumber, "Black", 1).partNumber()).isEqualTo(partNumber);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-3062", ".3062", "3062 b", "3062/b", "가나다", "123456789012345678901"})
    @DisplayName("부품 번호 형식이 틀리면 PART_REQUEST_INVALID")
    void partNumber_rejectsMalformed(String partNumber) {
        assertThatThrownBy(() -> new WantedPart(partNumber, "Black", 1))
                .isInstanceOf(InvalidPartRequestException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_INVALID");
    }

    @Test
    @DisplayName("부품 번호와 색 이름의 앞뒤 공백을 지운다")
    void wantedPart_trimsPartNumberAndColorName() {
        WantedPart part = new WantedPart("  3062b ", "  Black  ", 4);

        assertThat(part.partNumber()).isEqualTo("3062b");
        assertThat(part.colorName()).isEqualTo("Black");
    }

    @Test
    @DisplayName("색 이름은 공백 제거 후 1~30자")
    void colorName_mustBeOneToThirtyCharsAfterTrim() {
        assertThat(new WantedPart("3062b", "가".repeat(30), 1).colorName()).hasSize(30);
        assertThat(new WantedPart("3062b", "  " + "a".repeat(30) + "  ", 1).colorName())
                .hasSize(30);

        assertThatThrownBy(() -> new WantedPart("3062b", "   ", 1)).isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> new WantedPart("3062b", null, 1)).isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> new WantedPart("3062b", "a".repeat(31), 1))
                .isInstanceOf(InvalidPartRequestException.class);
    }

    @Test
    @DisplayName("수량은 1~999, 비어 있으면 거절")
    void quantity_mustBeBetweenOneAnd999() {
        assertThat(new WantedPart("3062b", "Black", 1).quantity()).isEqualTo(1);
        assertThat(new WantedPart("3062b", "Black", 999).quantity()).isEqualTo(999);

        assertThatThrownBy(() -> new WantedPart("3062b", "Black", 0)).isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> new WantedPart("3062b", "Black", 1000))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> WantedPart.of("3062b", "Black", null)).isInstanceOf(InvalidPartRequestException.class);
    }

    @Test
    @DisplayName("새 요청은 열린 상태이고 세트 번호·메모 공백을 지운다")
    void create_opensRequestAndNormalizesSetNumberAndNote() {
        PartRequest request = PartRequest.create("pr-1", "user-1", " 10305 ", List.of(BRICK), "  급해요  ", NOW);

        assertThat(request.getStatus()).isEqualTo(PartRequestStatus.OPEN);
        assertThat(request.isOpen()).isTrue();
        assertThat(request.getSetNumber()).isEqualTo("10305");
        assertThat(request.hasSet()).isTrue();
        assertThat(request.getNote()).isEqualTo("급해요");
        assertThat(request.getCreatedAt()).isEqualTo(NOW);
        assertThat(request.getClosedAt()).isNull();
        assertThat(request.getItems()).containsExactly(BRICK);
    }

    @Test
    @DisplayName("세트 번호가 비어 있으면 세트 없는 요청, 메모가 없으면 빈 문자열")
    void create_blankSetNumberMeansNoSet() {
        PartRequest request = PartRequest.create("pr-1", "user-1", "  ", List.of(BRICK), null, NOW);

        assertThat(request.getSetNumber()).isNull();
        assertThat(request.hasSet()).isFalse();
        assertThat(request.getNote()).isEmpty();
    }

    @Test
    @DisplayName("부품은 1~20개")
    void create_itemsMustBeOneToTwenty() {
        assertThat(PartRequest.create("pr-1", "user-1", null, Collections.nCopies(20, BRICK), "", NOW)
                        .getItems())
                .hasSize(20);

        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", null, List.of(), "", NOW))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", null, null, "", NOW))
                .isInstanceOf(InvalidPartRequestException.class);
        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", null, Collections.nCopies(21, BRICK), "", NOW))
                .isInstanceOf(InvalidPartRequestException.class);

        List<WantedPart> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", null, withNull, "", NOW))
                .isInstanceOf(InvalidPartRequestException.class);
    }

    @Test
    @DisplayName("메모는 공백 제거 후 500자 이하")
    void create_noteLimitedTo500CharsAfterTrim() {
        assertThat(PartRequest.create("pr-1", "user-1", null, List.of(BRICK), " " + "가".repeat(500) + " ", NOW)
                        .getNote())
                .hasSize(500);

        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", null, List.of(BRICK), "가".repeat(501), NOW))
                .isInstanceOf(InvalidPartRequestException.class);
    }

    @Test
    @DisplayName("세트 번호가 지나치게 길면 거절")
    void create_rejectsOverlongSetNumber() {
        assertThatThrownBy(() -> PartRequest.create("pr-1", "user-1", "1".repeat(31), List.of(BRICK), "", NOW))
                .isInstanceOf(InvalidPartRequestException.class);
    }

    @Test
    @DisplayName("마감하면 CLOSED와 마감 시각이 남고, 다시 마감하면 409")
    void close_marksClosedOnce() {
        PartRequest open = PartRequest.create("pr-1", "user-1", "10305", List.of(BRICK), "", NOW);
        Instant later = NOW.plusSeconds(60);

        PartRequest closed = open.close(later);

        assertThat(closed.getStatus()).isEqualTo(PartRequestStatus.CLOSED);
        assertThat(closed.getClosedAt()).isEqualTo(later);
        assertThat(closed.getCreatedAt()).isEqualTo(NOW);
        assertThat(open.isOpen()).isTrue();
        assertThatThrownBy(() -> closed.close(later))
                .isInstanceOf(PartRequestNotOpenException.class)
                .extracting("code")
                .isEqualTo("PART_REQUEST_NOT_OPEN");
    }

    @Test
    @DisplayName("작성자 판정")
    void isRequestedBy_matchesOnlyRequester() {
        PartRequest request = PartRequest.create("pr-1", "user-1", null, List.of(BRICK), "", NOW);

        assertThat(request.isRequestedBy("user-1")).isTrue();
        assertThat(request.isRequestedBy("user-2")).isFalse();
        assertThat(request.isRequestedBy(null)).isFalse();
    }
}
