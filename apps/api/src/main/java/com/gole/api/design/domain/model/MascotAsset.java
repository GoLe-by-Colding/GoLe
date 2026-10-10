package com.gole.api.design.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import java.util.Objects;

/**
 * 관리자가 올린 마스코트 에셋. (mascot-assets R2)
 *
 * <p>이미지는 미디어 컨텍스트가 저장·공개하고, 여기에는 저장 키와 그 키에서 만든 공개 경로만 둔다.
 * 어두운 배경용 이미지는 선택이다. 치수는 화면 비율을 잡는 용도라 범위만 검증한다.
 */
public record MascotAsset(
        String id,
        String name,
        String description,
        String imageKey,
        String imageUrl,
        String darkImageKey,
        String darkImageUrl,
        int width,
        int height,
        String createdBy,
        Instant createdAt) {

    public static final int NAME_MAX = 40;
    public static final int DESCRIPTION_MAX = 200;
    public static final int MIN_SIDE = 16;
    public static final int MAX_SIDE = 4096;

    public MascotAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(imageKey, "imageKey");
        Objects.requireNonNull(imageUrl, "imageUrl");
        description = description == null ? "" : description;
    }

    /** 새 에셋의 입력을 검증해 만든다. 이름·설명은 앞뒤 공백을 지운다. */
    public static MascotAsset register(
            String id,
            String name,
            String description,
            String imageKey,
            String imageUrl,
            String darkImageKey,
            String darkImageUrl,
            int width,
            int height,
            String createdBy,
            Instant createdAt) {
        validateInput(name, description, imageKey, darkImageKey, width, height);
        return new MascotAsset(
                id,
                name.strip(),
                description == null ? "" : description.strip(),
                imageKey,
                imageUrl,
                darkImageKey,
                darkImageUrl,
                width,
                height,
                createdBy,
                createdAt);
    }

    /** 미디어를 건드리기 전에 입력만 먼저 검증한다. */
    public static void validateInput(
            String name, String description, String imageKey, String darkImageKey, int width, int height) {
        String trimmedName = name == null ? "" : name.strip();
        if (trimmedName.isEmpty() || trimmedName.length() > NAME_MAX) {
            throw new BadRequestException("MASCOT_NAME_INVALID", "이름을 1~" + NAME_MAX + "자로 입력해 주세요");
        }
        if (description != null && description.strip().length() > DESCRIPTION_MAX) {
            throw new BadRequestException("MASCOT_DESCRIPTION_INVALID", "설명은 " + DESCRIPTION_MAX + "자까지 입력할 수 있습니다");
        }
        if (width < MIN_SIDE || width > MAX_SIDE || height < MIN_SIDE || height > MAX_SIDE) {
            throw new BadRequestException(
                    "MASCOT_IMAGE_SIZE_INVALID", "이미지는 가로·세로 " + MIN_SIDE + "~" + MAX_SIDE + "px이어야 합니다");
        }
        if (imageKey == null || imageKey.isBlank()) {
            throw new BadRequestException("MASCOT_IMAGE_REQUIRED", "마스코트 이미지를 올려 주세요");
        }
        if (imageKey.equals(darkImageKey)) {
            throw new BadRequestException("MASCOT_IMAGE_DUPLICATED", "어두운 배경용 이미지는 기본 이미지와 다른 파일이어야 합니다");
        }
    }
}
