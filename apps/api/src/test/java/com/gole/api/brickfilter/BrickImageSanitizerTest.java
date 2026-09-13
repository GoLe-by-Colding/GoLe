package com.gole.api.brickfilter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.brickfilter.adapter.out.provider.BrickImageSanitizer;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.media.application.port.in.NormalizeImageUseCase;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BrickImageSanitizerTest {
    byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void rejectsEmptyOrOversizedInputBeforeDecode() {
        var media = mock(NormalizeImageUseCase.class);
        var sanitizer = new BrickImageSanitizer(media);
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[0], false)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[4 * 1024 * 1024 + 1], false))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(media);
    }

    @Test
    void delegatesSniffedInputToCommonMediaAndProducesPng() throws Exception {
        var media = mock(NormalizeImageUseCase.class);
        byte[] input = png(3, 3);
        when(media.normalize(input)).thenReturn(new NormalizeImageUseCase.NormalizedImage(input, "image/png", 3, 3));
        assertThat(new BrickImageSanitizer(media).sanitize(input, false)).startsWith((byte) 0x89, (byte) 0x50);
        verify(media).normalize(input);
    }

    @Test
    void rejectsLargeResultDimensionsBeforeDecode() throws Exception {
        assertThatThrownBy(
                        () -> new BrickImageSanitizer(mock(NormalizeImageUseCase.class)).sanitize(png(6001, 1), true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void heifSignatureUsesSharedNormalizerWithoutBrickSpecificRejection() throws Exception {
        var media = mock(NormalizeImageUseCase.class);
        byte[] heif = java.nio.ByteBuffer.allocate(20)
                .putInt(20)
                .put("ftypheic".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .putInt(0)
                .put("heic".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .array();
        byte[] normalized = png(3, 3);
        when(media.normalize(heif))
                .thenReturn(new NormalizeImageUseCase.NormalizedImage(normalized, "image/png", 3, 3));
        assertThat(new BrickImageSanitizer(media).sanitize(heif, false)).startsWith((byte) 0x89, (byte) 0x50);
        verify(media).normalize(heif);
    }

    @Test
    void normalPhonePhotoIsDownscaledBeforeProvider() throws Exception {
        var media = mock(NormalizeImageUseCase.class);
        byte[] image = png(2000, 1000);
        when(media.normalize(image))
                .thenReturn(new NormalizeImageUseCase.NormalizedImage(image, "image/png", 2000, 1000));
        var decoded = ImageIO.read(new ByteArrayInputStream(new BrickImageSanitizer(media).sanitize(image, false)));
        assertThat(decoded.getWidth()).isEqualTo(1024);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }
}
