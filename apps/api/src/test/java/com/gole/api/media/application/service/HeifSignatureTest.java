package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.*;

import com.gole.api.media.domain.model.HeifSignature;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HeifSignatureTest {
    private byte[] box(String major, String compatible) {
        return ByteBuffer.allocate(24)
                .putInt(24)
                .put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put(major.getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put(compatible.getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .array();
    }

    @Test
    void acceptsStillHevcBrandAndRejectsSequencesAvifAndForgedSize() {
        assertThat(HeifSignature.matches(box("mif1", "heic"))).isTrue();
        assertThat(HeifSignature.matches(box("heix", "mif1"))).isTrue();
        assertThat(HeifSignature.matches(box("heic", "msf1"))).isFalse();
        assertThat(HeifSignature.matches(box("avif", "heic"))).isFalse();
        assertThat(HeifSignature.matches(box("mif1", "mif1"))).isFalse();
        byte[] forged = box("heic", "mif1");
        ByteBuffer.wrap(forged).putInt(Integer.MAX_VALUE);
        assertThat(HeifSignature.matches(forged)).isFalse();
    }
}
