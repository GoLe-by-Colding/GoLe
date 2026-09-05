package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.media.application.port.in.AuthorizeMediaReadUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase.LoadedImage;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.application.port.out.ImageProcessorPort.SanitizedImage;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.domain.exception.ImageNotFoundException;
import com.gole.api.media.domain.exception.ImageTooLargeException;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.StoredImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가짜 ObjectStoragePort로 프레임워크/네트워크 없이 미디어 유스케이스를 검증한다. (요구사항 M1, M2)
 */
class MediaServiceTest {

    private FakeStorage storage;
    private FakeProcessor processor;
    private Map<String, ObjectStoragePort.StoredObject> bundled;
    private MediaService service;
    private ManageMediaAssetsUseCase mediaAssets;
    private AuthorizeMediaReadUseCase authorization;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        processor = new FakeProcessor();
        bundled = new HashMap<>();
        mediaAssets = mock(ManageMediaAssetsUseCase.class);
        authorization = mock(AuthorizeMediaReadUseCase.class);
        service = new MediaService(
                storage,
                key -> Optional.ofNullable(bundled.get(key)),
                processor,
                mediaAssets,
                authorization,
                "https://gole.co.kr",
                1_000);
    }

    @Test
    void upload_storesObject_andReturnsPublicUrl() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "photo.PNG"));

        assertThat(stored.key()).startsWith("images/").endsWith(".png");
        assertThat(stored.url()).isEqualTo("https://gole.co.kr/api/v1/media/" + stored.key());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(storage.objects).containsKey(stored.key());
        verify(mediaAssets).registerStaged("owner-1", stored.key(), "image/png", pngBytes().length);
    }

    @Test
    void upload_storesOnlySanitizedBytes_andRegistersSanitizedSize() {
        byte[] normalized = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3};
        processor.sanitized = new SanitizedImage(normalized, "image/png", 10, 20);

        StoredImage stored = service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "photo.png"));

        assertThat(storage.objects.get(stored.key()).content()).isEqualTo(normalized);
        assertThat(stored.size()).isEqualTo(normalized.length);
        verify(mediaAssets).registerStaged("owner-1", stored.key(), "image/png", normalized.length);
    }

    @Test
    void upload_rejectsWhenNormalizedOutputExceedsStorageLimit() {
        processor.sanitized = new SanitizedImage(new byte[1_001], "image/png", 10, 20);

        assertThatThrownBy(
                        () -> service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "photo.png")))
                .isInstanceOf(ImageTooLargeException.class);

        assertThat(storage.objects).isEmpty();
    }

    @Test
    void upload_rejectsNonImage() {
        assertThatThrownBy(() -> service.upload(
                        new UploadImageCommand("owner-1", "data".getBytes(), "application/pdf", "x.pdf")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsEmpty() {
        assertThatThrownBy(() -> service.upload(new UploadImageCommand("owner-1", new byte[0], "image/png", "x.png")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsTooLarge() {
        byte[] big = new byte[1_001];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> service.upload(new UploadImageCommand("owner-1", big, "image/jpeg", "big.jpg")))
                .isInstanceOf(ImageTooLargeException.class);
    }

    @Test
    void upload_rejectsUnknownImageType() {
        assertThatThrownBy(
                        () -> service.upload(new UploadImageCommand("owner-1", "x".getBytes(), "image/tiff", "x.tiff")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsClaimedTypeThatDoesNotMatchBytes() {
        assertThatThrownBy(
                        () -> service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/jpeg", "fake.jpg")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsGifAndWebp_evenWhenDeclaredTypeMatches() {
        assertThatThrownBy(() -> service.upload(new UploadImageCommand("owner-1", gifBytes(), "image/gif", "a.gif")))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("JPEG and PNG");
        assertThatThrownBy(() -> service.upload(new UploadImageCommand("owner-1", webpBytes(), "image/webp", "a.webp")))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("JPEG and PNG");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void upload_removesObjectWhenAccessLedgerRegistrationFails() {
        doThrow(new IllegalStateException("mongo unavailable"))
                .when(mediaAssets)
                .registerStaged(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong());

        assertThatThrownBy(
                        () -> service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "photo.png")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storage.objects).isEmpty();
    }

    @Test
    void load_returnsStoredBytes() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "a.png"));

        LoadedImage loaded = service.load(stored.key());

        assertThat(loaded.content()).isEqualTo(pngBytes());
        assertThat(loaded.contentType()).isEqualTo("image/png");
    }

    @Test
    void load_returnsBundledAsset_withoutCallingObjectStorage() {
        bundled.put("catalog/10294.svg", new ObjectStoragePort.StoredObject("svg".getBytes(), "image/svg+xml"));
        storage.failOnGet = true;

        LoadedImage loaded = service.load("catalog/10294.svg");

        assertThat(loaded.content()).isEqualTo("svg".getBytes());
        assertThat(loaded.contentType()).isEqualTo("image/svg+xml");
    }

    @Test
    void loadResized_returnsBundledSvg_withoutCreatingDerivative() {
        bundled.put("catalog/10294.svg", new ObjectStoragePort.StoredObject("svg".getBytes(), "image/svg+xml"));
        storage.failOnGet = true;

        LoadedImage loaded = service.loadResized("catalog/10294.svg", 240);

        assertThat(loaded.content()).isEqualTo("svg".getBytes());
        assertThat(processor.calls).isZero();
    }

    @Test
    void load_throwsWhenMissing() {
        assertThatThrownBy(() -> service.load("images/none.png")).isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void load_rejectsDirectDerivativePathBeforeStorageAccess() {
        assertThatThrownBy(() -> service.load("derivatives/images/example.jpg/w240"))
                .isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void loadResized_cachesDerivative_andServesIt() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", jpegBytes(), "image/jpeg", "a.jpg"));
        processor.result = "thumb".getBytes();

        LoadedImage first = service.loadResized(stored.key(), 240);
        assertThat(first.content()).isEqualTo("thumb".getBytes());
        // 파생물이 캐시에 저장됨
        assertThat(storage.objects).containsKey("derivatives/" + stored.key() + "/w240");

        // 두 번째 호출은 캐시 사용 → 프로세서 재호출 없음
        processor.calls = 0;
        LoadedImage second = service.loadResized(stored.key(), 240);
        assertThat(second.content()).isEqualTo("thumb".getBytes());
        assertThat(processor.calls).isZero();
    }

    @Test
    void loadResized_servesOriginal_whenProcessorReturnsEmpty() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", pngBytes(), "image/png", "a.png"));
        processor.result = null; // 디코딩 불가 시뮬레이션

        LoadedImage result = service.loadResized(stored.key(), 240);

        assertThat(result.content()).isEqualTo(pngBytes());
        assertThat(storage.objects).doesNotContainKey("derivatives/" + stored.key() + "/w240");
    }

    @Test
    void loadResized_servesOriginal_whenWidthOutOfRange() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", jpegBytes(), "image/jpeg", "a.jpg"));

        LoadedImage result = service.loadResized(stored.key(), 5); // 범위 밖

        assertThat(result.content()).isEqualTo(jpegBytes());
        assertThat(processor.calls).isZero();
    }

    @Test
    void loadResized_servesOriginal_forWidthInsideOldRangeButOutsideAllowlist() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", jpegBytes(), "image/jpeg", "a.jpg"));
        processor.result = "unexpected".getBytes();

        LoadedImage result = service.loadResized(stored.key(), 241);

        assertThat(result.content()).isEqualTo(jpegBytes());
        assertThat(processor.calls).isZero();
        assertThat(storage.objects).doesNotContainKey("derivatives/" + stored.key() + "/w241");
    }

    @Test
    void loadResized_deletesDerivative_whenAssetIsRevokedDuringResize() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", jpegBytes(), "image/jpeg", "a.jpg"));
        processor.result = "thumb".getBytes();
        doNothing()
                .doThrow(new ImageNotFoundException(stored.key()))
                .when(authorization)
                .requireReadable(stored.key(), Optional.empty());

        assertThatThrownBy(() -> service.loadResized(stored.key(), 240)).isInstanceOf(ImageNotFoundException.class);

        assertThat(storage.objects).doesNotContainKey("derivatives/" + stored.key() + "/w240");
    }

    @Test
    void loadResized_requeuesDurableDeletion_whenRaceCompensationDeleteFails() {
        StoredImage stored = service.upload(new UploadImageCommand("owner-1", jpegBytes(), "image/jpeg", "a.jpg"));
        processor.result = "thumb".getBytes();
        storage.failOnDelete = true;
        doNothing()
                .doThrow(new ImageNotFoundException(stored.key()))
                .when(authorization)
                .requireReadable(stored.key(), Optional.empty());

        assertThatThrownBy(() -> service.loadResized(stored.key(), 240))
                .isInstanceOf(ImageNotFoundException.class)
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));

        verify(mediaAssets).requeueDeletion(stored.key());
    }

    private static final class FakeProcessor implements ImageProcessorPort {
        private byte[] result;
        private SanitizedImage sanitized;
        private int calls = 0;

        @Override
        public SanitizedImage sanitizeForStorage(byte[] source, String contentType) {
            return sanitized == null ? new SanitizedImage(source, contentType, 1, 1) : sanitized;
        }

        @Override
        public Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType) {
            calls++;
            return Optional.ofNullable(result);
        }
    }

    private static byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
    }

    private static byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    }

    private static byte[] webpBytes() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0x01};
    }

    private static byte[] gifBytes() {
        return new byte[] {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
    }

    private static final class FakeStorage implements ObjectStoragePort {
        private final Map<String, StoredObject> objects = new HashMap<>();
        private boolean bucketEnsured = false;
        private boolean failOnGet = false;
        private boolean failOnDelete = false;

        @Override
        public void ensureBucket() {
            bucketEnsured = true;
        }

        @Override
        public void put(String key, byte[] content, String contentType) {
            objects.put(key, new StoredObject(content, contentType));
        }

        @Override
        public Optional<StoredObject> get(String key) {
            if (failOnGet) {
                throw new AssertionError("object storage must not be called");
            }
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void delete(String key) {
            if (failOnDelete) {
                throw new IllegalStateException("storage unavailable");
            }
            objects.remove(key);
        }

        @Override
        public void deletePrefix(String prefix) {
            objects.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }
}
