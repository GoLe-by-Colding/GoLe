package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.domain.exception.InvalidImageException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NormalizeImageServiceTest {
    @Test
    void rejectsInvalidSignatureBeforeDecoder() {
        var processor = mock(ImageProcessorPort.class);
        var service = new NormalizeImageService(processor);
        for (byte[] input : new byte[][] {null, new byte[0], "<svg/>".getBytes(StandardCharsets.UTF_8)}) {
            assertThatThrownBy(() -> service.normalize(input)).isInstanceOf(InvalidImageException.class);
        }
        verifyNoInteractions(processor);
    }

    @Test
    void sniffsSupportedFormatsAndReturnsNormalizedContract() {
        var processor = mock(ImageProcessorPort.class);
        var service = new NormalizeImageService(processor);
        byte[][] inputs = {
            {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
            {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10},
            ByteBuffer.allocate(20)
                    .putInt(20)
                    .put("ftypheic".getBytes(StandardCharsets.US_ASCII))
                    .putInt(0)
                    .put("heic".getBytes(StandardCharsets.US_ASCII))
                    .array()
        };
        String[] types = {"image/jpeg", "image/png", "image/heic"};
        byte[] clean = {1, 2, 3};
        for (int i = 0; i < inputs.length; i++) {
            when(processor.sanitizeForStorage(inputs[i], types[i]))
                    .thenReturn(new ImageProcessorPort.SanitizedImage(clean, "image/jpeg", 48, 80));
            var result = service.normalize(inputs[i]);
            assertThat(result.content()).isEqualTo(clean);
            assertThat(result.contentType()).isEqualTo("image/jpeg");
            assertThat(result.width()).isEqualTo(48);
            assertThat(result.height()).isEqualTo(80);
            verify(processor).sanitizeForStorage(inputs[i], types[i]);
        }
    }
}
