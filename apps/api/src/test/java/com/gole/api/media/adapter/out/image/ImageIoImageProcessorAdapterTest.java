package com.gole.api.media.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.media.application.port.out.ImageProcessorPort.SanitizedImage;
import com.gole.api.media.domain.exception.InvalidImageException;
import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageIoImageProcessorAdapterTest {

    private final ImageIoImageProcessorAdapter processor = new ImageIoImageProcessorAdapter(8_192, 8_192, 16_000_000);

    @Test
    void jpeg_isDecodedToPixelsAndReencodedWithoutExifGpsIccOrComment() throws IOException {
        byte[] jpeg = image("jpeg", 16, 12, false);
        byte[] exif = "Exif\0\0GPSLatitude=37.123;GPSLongitude=127.123".getBytes(StandardCharsets.ISO_8859_1);
        byte[] icc = concat(
                "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1),
                new byte[] {1, 1},
                ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData());
        byte[] source = insertAfterJpegSoi(
                jpeg,
                jpegSegment(0xe1, exif),
                jpegSegment(0xe2, icc),
                jpegSegment(0xfe, "private-comment".getBytes(StandardCharsets.UTF_8)));

        SanitizedImage result = processor.sanitizeForStorage(source, "image/jpeg");

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(16);
        assertThat(result.height()).isEqualTo(12);
        assertThat(contains(result.content(), "GPSLatitude".getBytes(StandardCharsets.US_ASCII)))
                .isFalse();
        assertThat(contains(result.content(), "ICC_PROFILE".getBytes(StandardCharsets.US_ASCII)))
                .isFalse();
        assertThat(contains(result.content(), "private-comment".getBytes(StandardCharsets.US_ASCII)))
                .isFalse();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.content())))
                .extracting(BufferedImage::getWidth, BufferedImage::getHeight)
                .containsExactly(16, 12);
    }

    @Test
    void png_isReencodedWithoutTextChunk() throws IOException {
        byte[] source = insertPngChunkAfterIhdr(
                image("png", 9, 7, true), "tEXt", "location\0secret-home".getBytes(StandardCharsets.ISO_8859_1));

        SanitizedImage result = processor.sanitizeForStorage(source, "image/png");

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(contains(result.content(), "secret-home".getBytes(StandardCharsets.US_ASCII)))
                .isFalse();
        assertThat(containsPngChunk(result.content(), "tEXt")).isFalse();
    }

    @Test
    void apng_isRejectedInsteadOfSilentlyKeepingOnlyOneFrame() throws IOException {
        byte[] animated =
                insertPngChunkAfterIhdr(image("png", 2, 2, true), "acTL", new byte[] {0, 0, 0, 2, 0, 0, 0, 0});

        assertThatThrownBy(() -> processor.sanitizeForStorage(animated, "image/png"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Animated");
    }

    @Test
    void forgedHugePngHeader_isRejectedBeforePixelDecode() throws IOException {
        byte[] forged = image("png", 1, 1, true);
        writeInt(forged, 16, 100_000);
        updatePngChunkCrc(forged, 8);

        assertThatThrownBy(() -> processor.sanitizeForStorage(forged, "image/png"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("dimensions");
    }

    @Test
    void totalPixelLimit_isCheckedIndependentlyFromAxisLimits() throws IOException {
        ImageIoImageProcessorAdapter strictPixels = new ImageIoImageProcessorAdapter(100, 100, 99);

        assertThatThrownBy(() -> strictPixels.sanitizeForStorage(image("png", 10, 10, true), "image/png"))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("dimensions");
    }

    @Test
    void gifAndWebp_areExplicitlyRejectedRegardlessOfAnimationState() {
        assertThatThrownBy(() -> processor.sanitizeForStorage(new byte[] {'G', 'I', 'F', '8', '9', 'a'}, "image/gif"))
                .isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> processor.sanitizeForStorage(
                        new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, "image/webp"))
                .isInstanceOf(InvalidImageException.class);
    }

    private static byte[] image(String format, int width, int height, boolean alpha) throws IOException {
        BufferedImage image =
                new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(40, 120, 220, alpha ? 180 : 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    private static byte[] jpegSegment(int marker, byte[] payload) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(0xff);
            output.writeByte(marker);
            output.writeShort(payload.length + 2);
            output.write(payload);
            return bytes.toByteArray();
        }
    }

    private static byte[] insertAfterJpegSoi(byte[] jpeg, byte[]... segments) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, 2);
            for (byte[] segment : segments) {
                output.write(segment);
            }
            output.write(jpeg, 2, jpeg.length - 2);
            return output.toByteArray();
        }
    }

    private static byte[] insertPngChunkAfterIhdr(byte[] png, String type, byte[] data) throws IOException {
        byte[] chunk = pngChunk(type, data);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(png, 0, 33);
            output.write(chunk);
            output.write(png, 33, png.length - 33);
            return output.toByteArray();
        }
    }

    private static byte[] pngChunk(String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(data.length);
            output.write(typeBytes);
            output.write(data);
            output.writeInt((int) crc.getValue());
            return bytes.toByteArray();
        }
    }

    private static boolean containsPngChunk(byte[] png, String expectedType) {
        int offset = 8;
        while (offset <= png.length - 12) {
            int length = readInt(png, offset);
            if (length < 0 || (long) offset + length + 12 > png.length) {
                return false;
            }
            String type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
            if (expectedType.equals(type)) {
                return true;
            }
            offset += length + 12;
        }
        return false;
    }

    private static void updatePngChunkCrc(byte[] png, int chunkOffset) {
        int length = readInt(png, chunkOffset);
        CRC32 crc = new CRC32();
        crc.update(png, chunkOffset + 4, length + 4);
        writeInt(png, chunkOffset + 8 + length, (int) crc.getValue());
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(haystack, i, i + needle.length), needle)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] concat(byte[]... values) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (byte[] value : values) {
                output.write(value);
            }
            return output.toByteArray();
        }
    }
}
