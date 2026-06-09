package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.media.application.port.in.LoadImageUseCase.LoadedImage;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.application.port.out.ImageProcessorPort;
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
    private MediaService service;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        processor = new FakeProcessor();
        service = new MediaService(storage, processor, "https://gole.kscold.com", 1_000);
    }

    @Test
    void upload_storesObject_andReturnsPublicUrl() {
        StoredImage stored = service.upload(
                new UploadImageCommand("hello".getBytes(), "image/png", "photo.PNG"));

        assertThat(stored.key()).startsWith("images/").endsWith(".png");
        assertThat(stored.url())
                .isEqualTo("https://gole.kscold.com/api/v1/media/" + stored.key());
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(storage.objects).containsKey(stored.key());
    }

    @Test
    void upload_rejectsNonImage() {
        assertThatThrownBy(() -> service.upload(
                new UploadImageCommand("data".getBytes(), "application/pdf", "x.pdf")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsEmpty() {
        assertThatThrownBy(() -> service.upload(
                new UploadImageCommand(new byte[0], "image/png", "x.png")))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void upload_rejectsTooLarge() {
        byte[] big = new byte[1_001];
        assertThatThrownBy(() -> service.upload(
                new UploadImageCommand(big, "image/jpeg", "big.jpg")))
                .isInstanceOf(ImageTooLargeException.class);
    }

    @Test
    void upload_usesBinExtension_forUnknownImageType() {
        StoredImage stored = service.upload(
                new UploadImageCommand("x".getBytes(), "image/tiff", "x.tiff"));
        assertThat(stored.key()).endsWith(".bin");
    }

    @Test
    void load_returnsStoredBytes() {
        StoredImage stored = service.upload(
                new UploadImageCommand("bytes".getBytes(), "image/webp", "a.webp"));

        LoadedImage loaded = service.load(stored.key());

        assertThat(loaded.content()).isEqualTo("bytes".getBytes());
        assertThat(loaded.contentType()).isEqualTo("image/webp");
    }

    @Test
    void load_throwsWhenMissing() {
        assertThatThrownBy(() -> service.load("images/none.png"))
                .isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    void loadResized_cachesDerivative_andServesIt() {
        StoredImage stored = service.upload(
                new UploadImageCommand("original".getBytes(), "image/jpeg", "a.jpg"));
        processor.result = "thumb".getBytes();

        LoadedImage first = service.loadResized(stored.key(), 240);
        assertThat(first.content()).isEqualTo("thumb".getBytes());
        // 파생물이 캐시에 저장됨
        assertThat(storage.objects).containsKey("derivatives/w240/" + stored.key());

        // 두 번째 호출은 캐시 사용 → 프로세서 재호출 없음
        processor.calls = 0;
        LoadedImage second = service.loadResized(stored.key(), 240);
        assertThat(second.content()).isEqualTo("thumb".getBytes());
        assertThat(processor.calls).isZero();
    }

    @Test
    void loadResized_servesOriginal_whenProcessorReturnsEmpty() {
        StoredImage stored = service.upload(
                new UploadImageCommand("original".getBytes(), "image/webp", "a.webp"));
        processor.result = null; // 디코딩 불가 시뮬레이션

        LoadedImage result = service.loadResized(stored.key(), 240);

        assertThat(result.content()).isEqualTo("original".getBytes());
        assertThat(storage.objects).doesNotContainKey("derivatives/w240/" + stored.key());
    }

    @Test
    void loadResized_servesOriginal_whenWidthOutOfRange() {
        StoredImage stored = service.upload(
                new UploadImageCommand("original".getBytes(), "image/jpeg", "a.jpg"));

        LoadedImage result = service.loadResized(stored.key(), 5); // 범위 밖

        assertThat(result.content()).isEqualTo("original".getBytes());
        assertThat(processor.calls).isZero();
    }

    private static final class FakeProcessor implements ImageProcessorPort {
        private byte[] result;
        private int calls = 0;

        @Override
        public Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType) {
            calls++;
            return Optional.ofNullable(result);
        }
    }

    private static final class FakeStorage implements ObjectStoragePort {
        private final Map<String, StoredObject> objects = new HashMap<>();
        private boolean bucketEnsured = false;

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
            return Optional.ofNullable(objects.get(key));
        }
    }
}
