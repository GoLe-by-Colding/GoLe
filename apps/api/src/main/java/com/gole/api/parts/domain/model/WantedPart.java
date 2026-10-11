package com.gole.api.parts.domain.model;

import com.gole.api.parts.domain.exception.InvalidPartRequestException;
import java.util.regex.Pattern;

/**
 * 찾는 부품 한 줄: 부품 번호·색·수량. (wanted-parts W1)
 *
 * <p>부품 카탈로그가 아직 없어 번호·색은 자유 입력이다. 그래서 형식만 막는다 — 번호는 BrickLink·
 * Rebrickable 표기({@code 3062b}, {@code 973pb1234c01}, {@code 3626cpr0001.1})가 들어갈 만큼만 연다.
 * 앞뒤 공백은 지우고 검사한다. 복사·붙여넣기로 딸려 온 공백 때문에 거절하지 않기 위해서다.
 */
public record WantedPart(String partNumber, String colorName, int quantity) {

    public static final int MAX_COLOR_NAME_LENGTH = 30;
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 999;

    private static final Pattern PART_NUMBER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9.\\-]{0,19}$");

    public WantedPart {
        partNumber = partNumber == null ? "" : partNumber.trim();
        if (!PART_NUMBER.matcher(partNumber).matches()) {
            throw new InvalidPartRequestException("부품 번호는 영문·숫자로 시작하는 20자 이하(영문·숫자·.·-)여야 합니다");
        }
        colorName = colorName == null ? "" : colorName.trim();
        if (colorName.isEmpty() || colorName.length() > MAX_COLOR_NAME_LENGTH) {
            throw new InvalidPartRequestException("색 이름은 1~30자여야 합니다");
        }
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidPartRequestException("수량은 1~999개여야 합니다");
        }
    }

    /** 요청 본문처럼 수량이 비어 올 수 있는 입력용. 비어 있으면 400이다. */
    public static WantedPart of(String partNumber, String colorName, Integer quantity) {
        if (quantity == null) {
            throw new InvalidPartRequestException("수량은 1~999개여야 합니다");
        }
        return new WantedPart(partNumber, colorName, quantity);
    }
}
