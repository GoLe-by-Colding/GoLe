package com.gole.api.account.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 온보딩에서 고를 수 있는 관심 태그 목록. (onboarding D8)
 *
 * <p>신규 컬렉션을 만들지 않고 curated 상수로 시작한다. {@code catalog.LegoSet.theme}는 세트당
 * 자유 텍스트 단일 값이라 다중 선택 태그로 바로 쓸 수 없다. 실제 테마 인기도 연동은 후속 과제.
 *
 * <p>목록을 도메인에 두는 이유 — 선택값 검증(1~5개, 목록 안의 값만)이 목록 자체와 같은 곳에
 * 있어야 한 쪽만 바뀌는 사고가 나지 않는다.
 */
public final class InterestTagCatalog {

    /** 사용자가 고를 수 있는 최소/최대 개수. (요구사항 R6) */
    public static final int MIN_SELECTION = 1;

    public static final int MAX_SELECTION = 5;

    private static final List<String> TAGS = List.of(
            "스타워즈", "테크닉", "크리에이터", "아키텍처", "시티", "닌자고", "해리포터", "아이디어", "슈퍼히어로", "프렌즈", "듀플로", "아이콘", "스피드챔피언",
            "마인크래프트");

    private InterestTagCatalog() {}

    /** 노출용 전체 목록. */
    public static List<String> tags() {
        return TAGS;
    }

    /**
     * 선택값 검증 후 정규화한다. 중복은 제거하고 입력 순서를 보존한다.
     *
     * @throws BadRequestException 개수가 범위를 벗어나거나 목록에 없는 태그가 섞인 경우
     */
    public static Set<String> validateSelection(Set<String> selected) {
        if (selected == null || selected.isEmpty()) {
            throw new BadRequestException("INVALID_INTEREST_TAGS", "관심 태그를 1개 이상 선택해 주세요");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : selected) {
            String trimmed = tag == null ? "" : tag.trim();
            if (!TAGS.contains(trimmed)) {
                throw new BadRequestException("INVALID_INTEREST_TAGS", "선택할 수 없는 관심 태그입니다: " + trimmed);
            }
            normalized.add(trimmed);
        }
        if (normalized.size() < MIN_SELECTION || normalized.size() > MAX_SELECTION) {
            throw new BadRequestException(
                    "INVALID_INTEREST_TAGS", "관심 태그는 " + MIN_SELECTION + "~" + MAX_SELECTION + "개를 선택해 주세요");
        }
        return normalized;
    }
}
