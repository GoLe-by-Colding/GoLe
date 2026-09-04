package com.gole.api.media.application.service;

import com.gole.api.media.application.port.in.AuthorizeMediaReadUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.out.BundledMediaPort;
import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.application.port.out.ImageProcessorPort.SanitizedImage;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.application.port.out.ObjectStoragePort.StoredObject;
import com.gole.api.media.domain.exception.ImageNotFoundException;
import com.gole.api.media.domain.exception.ImageTooLargeException;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.MediaKey;
import com.gole.api.media.domain.model.StoredImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            "image/png", "png");

    /** 공개 객체 하나당 생성 가능한 파생물을 네 개로 제한한다. */
    private static final Set<Integer> ALLOWED_THUMB_WIDTHS = Set.of(240, 480, 960, 1600);

    private final ObjectStoragePort objectStorage;
    private final BundledMediaPort bundledMedia;
    private final ImageProcessorPort imageProcessor;
    private final ManageMediaAssetsUseCase mediaAssets;
    private final AuthorizeMediaReadUseCase mediaReadAuthorization;
    private final String publicBaseUrl;
    private final long maxImageBytes;

    public MediaService(
            ObjectStoragePort objectStorage,
            BundledMediaPort bundledMedia,
            ImageProcessorPort imageProcessor,
            ManageMediaAssetsUseCase mediaAssets,
            AuthorizeMediaReadUseCase mediaReadAuthorization,
            String publicBaseUrl,
            long maxImageBytes) {
        this.objectStorage = objectStorage;
        this.bundledMedia = bundledMedia;
        this.imageProcessor = imageProcessor;
        this.mediaAssets = mediaAssets;
        this.mediaReadAuthorization = mediaReadAuthorization;
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
        // M1.3: 크기 한도
        if (content.length > maxImageBytes) {
            throw new ImageTooLargeException(maxImageBytes);
        }

        // 브라우저가 보낸 MIME 문자열은 위조할 수 있다. 공개 제공해도 안전한 래스터 형식만
        // 파일 시그니처로 판별하고, 선언 형식과 실제 형식이 다르면 거부한다.
        String detectedType = detectContentType(content)
                .orElseThrow(() -> new InvalidImageException("Only JPEG and PNG images are allowed"));
        String declaredType = normalizeDeclaredType(contentType);
        if (!detectedType.equals(declaredType)) {
            throw new InvalidImageException("Declared image type does not match file content");
        }
        if (!EXTENSION_BY_TYPE.containsKey(detectedType)) {
            // JDK ImageIO가 안전하게 완전 재인코딩할 수 없는 GIF/WebP는 정지 이미지도 받지 않는다.
            throw new InvalidImageException("Only non-animated JPEG and PNG images are allowed");
        }

        SanitizedImage sanitized = imageProcessor.sanitizeForStorage(content, detectedType);
        if (sanitized == null
                || sanitized.content() == null
                || sanitized.content().length == 0
                || !detectedType.equals(sanitized.contentType())) {
            throw new InvalidImageException("Image could not be safely normalized");
        }
        if (sanitized.content().length > maxImageBytes) {
            throw new ImageTooLargeException(maxImageBytes);
        }

        // M1.4: 원본 파일명 미신뢰, 충돌 없는 키 생성
        String normalizedType = sanitized.contentType();
        String extension = EXTENSION_BY_TYPE.get(normalizedType);
        String key = "images/" + UUID.randomUUID() + "." + extension;

        objectStorage.put(key, sanitized.content(), normalizedType);

        try {
            mediaAssets.registerStaged(command.ownerId(), key, normalizedType, sanitized.content().length);
        } catch (RuntimeException registrationFailure) {
            // DB 원장 없는 객체는 절대 공개되지 않지만 불필요한 orphan도 남기지 않는다.
            try {
                objectStorage.delete(key);
            } catch (RuntimeException cleanupFailure) {
                registrationFailure.addSuppressed(cleanupFailure);
            }
            throw registrationFailure;
        }

        return new StoredImage(key, publicUrl(key), normalizedType, sanitized.content().length);
    }

    @Override
    public LoadedImage load(String key, Optional<String> viewerId) {
        Optional<StoredObject> bundled = bundledMedia.get(key);
        if (bundled.isPresent()) {
            return new LoadedImage(bundled.get().content(), bundled.get().contentType());
        }
        if (!MediaKey.isUserKey(key)) {
            throw new ImageNotFoundException(key);
        }
        mediaReadAuthorization.requireReadable(key, viewerId);
        StoredObject object = objectStorage.get(key).orElseThrow(() -> new ImageNotFoundException(key));
        return new LoadedImage(object.content(), object.contentType());
    }

    @Override
    public LoadedImage loadResized(String key, int width, Optional<String> viewerId) {
        Optional<StoredObject> bundled = bundledMedia.get(key);
        if (bundled.isPresent()) {
            return new LoadedImage(bundled.get().content(), bundled.get().contentType());
        }
        if (!MediaKey.isUserKey(key)) {
            // 파생 키를 URL 경로로 직접 요청하는 우회를 포함해 원본 키 외에는 모두 숨긴다.
            throw new ImageNotFoundException(key);
        }
        mediaReadAuthorization.requireReadable(key, viewerId);
        // allowlist 밖 폭은 원본으로 처리해 공격자가 임의 폭 파생물을 만들 수 없게 한다.
        if (!ALLOWED_THUMB_WIDTHS.contains(width)) {
            StoredObject original = objectStorage.get(key).orElseThrow(() -> new ImageNotFoundException(key));
            return new LoadedImage(original.content(), original.contentType());
        }

        String derivativeKey = MediaKey.derivativePrefix(key) + "w" + width;
        Optional<StoredObject> cached = objectStorage.get(derivativeKey);
        if (cached.isPresent()) {
            return new LoadedImage(cached.get().content(), cached.get().contentType());
        }

        StoredObject original = objectStorage.get(key).orElseThrow(() -> new ImageNotFoundException(key));
        Optional<byte[]> resized = imageProcessor.resizeToWidth(original.content(), width, original.contentType());
        if (resized.isEmpty()) {
            // 디코딩 불가/업스케일 불필요 → 원본 제공(캐시하지 않음).
            return new LoadedImage(original.content(), original.contentType());
        }

        objectStorage.put(derivativeKey, resized.get(), original.contentType());
        try {
            // 최초 권한 확인과 변형 저장 사이에 revoke worker가 prefix를 지울 수 있다. 저장 직후
            // 다시 확인하고 revoke됐다면 방금 만든 파생물을 보상 삭제한다.
            mediaReadAuthorization.requireReadable(key, viewerId);
        } catch (RuntimeException noLongerReadable) {
            try {
                objectStorage.delete(derivativeKey);
            } catch (RuntimeException cleanupFailure) {
                noLongerReadable.addSuppressed(cleanupFailure);
                try {
                    // worker가 이미 COMPLETED인 경합도 다시 PENDING으로 되돌려 물리 orphan을 남기지 않는다.
                    mediaAssets.requeueDeletion(key);
                } catch (RuntimeException journalFailure) {
                    noLongerReadable.addSuppressed(journalFailure);
                }
            }
            throw noLongerReadable;
        }
        return new LoadedImage(resized.get(), original.contentType());
    }

    private String publicUrl(String key) {
        return publicBaseUrl + MediaKey.publicPath(key);
    }

    private static String normalizeDeclaredType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.toLowerCase().split(";", 2)[0].trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private static Optional<String> detectContentType(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (asciiAt(bytes, 0, "GIF87a") || asciiAt(bytes, 0, "GIF89a")) {
            return Optional.of("image/gif");
        }
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WEBP")) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String signature) {
        if (bytes.length < offset + signature.length()) {
            return false;
        }
        for (int i = 0; i < signature.length(); i++) {
            if (bytes[offset + i] != (byte) signature.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
