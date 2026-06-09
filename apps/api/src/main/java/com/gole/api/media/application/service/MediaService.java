package com.gole.api.media.application.service;

import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.application.port.out.ObjectStoragePort.StoredObject;
import com.gole.api.media.domain.exception.ImageNotFoundException;
import com.gole.api.media.domain.exception.ImageTooLargeException;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.StoredImage;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 미디어 유스케이스 구현(업로드/조회). 포트에만 의존하는 순수 애플리케이션 서비스.
 * (요구사항 M1, M2)
 *
 * <p>스프링 빈 등록은 어댑터/설정 레이어(@Configuration)에서 수행하여
 * application 레이어를 프레임워크로부터 자유롭게 유지한다.
 */
public class MediaService implements UploadImageUseCase, LoadImageUseCase {

    /** 공개 조회 경로 프리픽스. {@code GET /api/v1/media/{key}} 와 일치해야 한다.
     *  (key 자체가 {@code images/<uuid>.ext} 형태이므로 여기서 images 를 중복하지 않는다.) */
    public static final String PUBLIC_PATH_PREFIX = "/api/v1/media/";

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    /** 썸네일 허용 폭 범위(캐시 변형 폭주 방지). (백로그 N2a) */
    private static final int MIN_THUMB_WIDTH = 32;
    private static final int MAX_THUMB_WIDTH = 1600;

    private final ObjectStoragePort objectStorage;
    private final ImageProcessorPort imageProcessor;
    private final String publicBaseUrl;
    private final long maxImageBytes;

    public MediaService(
            ObjectStoragePort objectStorage,
            ImageProcessorPort imageProcessor,
            String publicBaseUrl,
            long maxImageBytes) {
        this.objectStorage = objectStorage;
        this.imageProcessor = imageProcessor;
        // 끝 슬래시 제거(중복 방지). null이면 빈 문자열 → 상대경로 URL.
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.maxImageBytes = maxImageBytes;
    }

    @Override
    public StoredImage upload(UploadImageCommand command) {
        byte[] content = command.content();
        String contentType = command.contentType();

        // M1.2: 비었거나 이미지가 아니면 거부
        if (content == null || content.length == 0) {
            throw new InvalidImageException("Uploaded file is empty");
        }
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new InvalidImageException("Only image content types are allowed");
        }
        // M1.3: 크기 한도
        if (content.length > maxImageBytes) {
            throw new ImageTooLargeException(maxImageBytes);
        }

        // M1.4: 원본 파일명 미신뢰, 충돌 없는 키 생성
        String normalizedType = contentType.toLowerCase();
        String extension = EXTENSION_BY_TYPE.getOrDefault(normalizedType, "bin");
        String key = "images/" + UUID.randomUUID() + "." + extension;

        objectStorage.put(key, content, normalizedType);

        return new StoredImage(key, publicUrl(key), normalizedType, content.length);
    }

    @Override
    public LoadedImage load(String key) {
        StoredObject object = objectStorage.get(key).orElseThrow(() -> new ImageNotFoundException(key));
        return new LoadedImage(object.content(), object.contentType());
    }

    @Override
    public LoadedImage loadResized(String key, int width) {
        // 범위 밖 폭은 원본으로 안전 처리(캐시 변형 폭주 방지).
        if (width < MIN_THUMB_WIDTH || width > MAX_THUMB_WIDTH) {
            return load(key);
        }

        String derivativeKey = "derivatives/w" + width + "/" + key;
        Optional<StoredObject> cached = objectStorage.get(derivativeKey);
        if (cached.isPresent()) {
            return new LoadedImage(cached.get().content(), cached.get().contentType());
        }

        StoredObject original = objectStorage.get(key).orElseThrow(() -> new ImageNotFoundException(key));
        Optional<byte[]> resized =
                imageProcessor.resizeToWidth(original.content(), width, original.contentType());
        if (resized.isEmpty()) {
            // 디코딩 불가/업스케일 불필요 → 원본 제공(캐시하지 않음).
            return new LoadedImage(original.content(), original.contentType());
        }

        objectStorage.put(derivativeKey, resized.get(), original.contentType());
        return new LoadedImage(resized.get(), original.contentType());
    }

    private String publicUrl(String key) {
        return publicBaseUrl + PUBLIC_PATH_PREFIX + key;
    }
}
