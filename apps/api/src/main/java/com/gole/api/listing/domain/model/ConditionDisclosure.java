package com.gole.api.listing.domain.model;

import java.util.Objects;

/**
 * 상품 상태 고지 값 객체. 판매자가 구성/박스/설명서/누락부품/하자를 명확히 고지한다.
 * 누락 부품이 있으면 상세 설명이 반드시 있어야 한다(불변식).
 *
 * @param completeness     구성(풀박스/박스없음/벌크)
 * @param hasBox           박스 포함 여부
 * @param hasManual        설명서 포함 여부
 * @param hasMissingParts  누락 부품 존재 여부
 * @param missingPartsNote 누락 부품 상세(누락 시 필수, 그 외 빈 문자열 허용)
 * @param defectsNote      하자/손상(뭉개짐·변색·파손 등) 설명(선택)
 */
public record ConditionDisclosure(
        Completeness completeness,
        boolean hasBox,
        boolean hasManual,
        boolean hasMissingParts,
        String missingPartsNote,
        String defectsNote) {

    public ConditionDisclosure {
        Objects.requireNonNull(completeness, "completeness");
        missingPartsNote = missingPartsNote == null ? "" : missingPartsNote.trim();
        defectsNote = defectsNote == null ? "" : defectsNote.trim();
        if (hasMissingParts && missingPartsNote.isBlank()) {
            throw new IllegalArgumentException("누락 부품이 있으면 상세 설명이 필요합니다");
        }
        if (missingPartsNote.length() > 1000 || defectsNote.length() > 1000) {
            throw new IllegalArgumentException("고지 설명은 1000자를 넘을 수 없습니다");
        }
    }

    /** 레거시/기본 고지: 박스 없음, 누락/하자 없음. */
    public static ConditionDisclosure basic() {
        return new ConditionDisclosure(Completeness.NO_BOX, false, false, false, "", "");
    }
}
