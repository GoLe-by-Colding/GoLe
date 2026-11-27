package com.gole.api.media.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** 제한된 범위의 ISO-BMFF ftyp 검사. HEVC 정지 이미지만 허용하며 AVIF와 연속 이미지는 제외한다. */
public final class HeifSignature {
    private HeifSignature() {}

    public static boolean matches(byte[] bytes) {
        if (bytes == null || bytes.length < 20) return false;
        int length = ByteBuffer.wrap(bytes).getInt();
        if (length < 20
                || length > 4096
                || length > bytes.length
                || length % 4 != 0
                || !brand(bytes, 4).equals("ftyp")) return false;
        boolean hevc = false;
        for (int i = 8; i < length; i += 4) {
            if (i == 12) continue; // minor version, not a brand
            String brand = brand(bytes, i);
            if (brand.equals("avif")
                    || brand.equals("avis")
                    || brand.equals("msf1")
                    || brand.equals("hevc")
                    || brand.equals("hevx")) return false;
            if (brand.equals("heic") || brand.equals("heix")) hevc = true;
        }
        return hevc;
    }

    private static String brand(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
