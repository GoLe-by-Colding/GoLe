package com.gole.api.media.adapter.out.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.media.application.port.in.*;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.application.service.MediaService;
import com.gole.api.media.domain.exception.InvalidImageException;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class HeifImageProcessorTest {
    private byte[] fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/media/phone-oriented-gps.heic")) {
            return input.readAllBytes();
        }
    }

    private ImageIoImageProcessorAdapter processor(String python, long pixels) {
        return new ImageIoImageProcessorAdapter(8192, 8192, pixels, 5_242_880, python, Duration.ofSeconds(10));
    }

    private java.util.Set<Path> decoderTemps() throws Exception {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("gole-heif-"))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test
    void missingDecoderFailsClosedWithConversionAdvice() throws Exception {
        byte[] bytes = fixture();
        assertThatThrownBy(
                        () -> processor("/nonexistent/gole-python", 16_000_000).sanitizeForStorage(bytes, "image/heic"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("JPEG/PNG")
                .hasMessageContaining("지원하지");
    }

    @Test
    void mismatchedSignatureAndOversizedBytesNeverReachDecoder() {
        var processor = new ImageIoImageProcessorAdapter(
                8192, 8192, 16_000_000, 30, "/nonexistent/python", Duration.ofSeconds(10));
        assertThatThrownBy(() ->
                        processor.sanitizeForStorage(new byte[] {(byte) 255, (byte) 216, (byte) 255}, "image/heic"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("decoded");
        assertThatThrownBy(() -> processor.sanitizeForStorage(new byte[31], "image/heic"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("bytes");
    }

    @Test
    void stalledDecoderIsKilledWithinTimeout(@TempDir Path temporary) throws Exception {
        Path script = temporary.resolve("stalled-python");
        Files.writeString(script, "#!/bin/sh\nexec /bin/sleep 30\n");
        Files.setPosixFilePermissions(script, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        var processor = new ImageIoImageProcessorAdapter(
                8192, 8192, 16_000_000, 5_242_880, script.toString(), Duration.ofMillis(100));
        byte[] bytes = fixture();
        java.util.Set<Path> existingTemps = decoderTemps();
        long before = System.nanoTime();
        assertThatThrownBy(() -> processor.sanitizeForStorage(bytes, "image/heic"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("시간");
        assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(3));
        assertThat(decoderTemps()).isEqualTo(existingTemps);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOLE_HEIF_TEST_PYTHON", matches = ".+")
    void realHeicRotatesAndRemovesExifGpsThroughCommonUploadPipeline() throws Exception {
        var processor = processor(System.getenv("GOLE_HEIF_TEST_PYTHON"), 16_000_000);
        byte[] original = fixture();
        var storage = mock(ObjectStoragePort.class);
        var assets = mock(ManageMediaAssetsUseCase.class);
        var service = new MediaService(
                storage,
                key -> Optional.empty(),
                processor,
                assets,
                mock(AuthorizeMediaReadUseCase.class),
                "",
                5_242_880);
        var uploaded = service.upload(
                new UploadImageUseCase.UploadImageCommand("owner", original, "image/heif", "phone.HEIF"));
        assertThat(uploaded.contentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.key()).endsWith(".jpg");
        var content = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(eq(uploaded.key()), content.capture(), eq("image/jpeg"));
        var image = ImageIO.read(new ByteArrayInputStream(content.getValue()));
        assertThat(image.getWidth()).isEqualTo(48);
        assertThat(image.getHeight()).isEqualTo(80);
        assertThat(new Color(image.getRGB(10, 10)).getRed()).isGreaterThan(200);
        assertThat(new Color(image.getRGB(10, 70)).getBlue()).isGreaterThan(200);
        String encoded = new String(content.getValue(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(encoded).doesNotContain("Exif", "GOLE-GPS-FIXTURE", "GPS");
        verify(assets).registerStaged("owner", uploaded.key(), "image/jpeg", content.getValue().length);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOLE_HEIF_TEST_PYTHON", matches = ".+")
    void realDecoderRejectsCorruptAndPixelBomb() throws Exception {
        byte[] original = fixture();
        var processor = processor(System.getenv("GOLE_HEIF_TEST_PYTHON"), 16_000_000);
        assertThatThrownBy(() -> processor.sanitizeForStorage(Arrays.copyOf(original, 64), "image/heic"))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> processor(System.getenv("GOLE_HEIF_TEST_PYTHON"), 100)
                        .sanitizeForStorage(original, "image/heic"))
                .isInstanceOf(InvalidImageException.class);
    }
}
