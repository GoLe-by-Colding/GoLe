package com.gole.api.media.adapter.out.image;

import com.gole.api.media.adapter.out.s3.StorageProperties;
import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.domain.exception.InvalidImageException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JDK {@link ImageIO} 기반 이미지 정규화/리사이즈 어댑터.
 *
 * <p>사용자 업로드는 JPEG/PNG 정지 이미지만 허용한다. 헤더 치수를 확인한 뒤 픽셀을 표준 RGB/ARGB
 * 버퍼로 디코딩하고 메타데이터 없이 재인코딩하므로 EXIF/GPS/ICC/코멘트가 저장되지 않는다. JDK가
 * 안전하게 완전 디코딩/재인코딩할 수 없는 GIF/WebP와 APNG는 업로드 단계에서 거부한다.
 */
@Component
public class ImageIoImageProcessorAdapter implements ImageProcessorPort {

    private static final Logger log = LoggerFactory.getLogger(ImageIoImageProcessorAdapter.class);

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final int maxWidth;
    private final int maxHeight;
    private final long maxPixels;

    @Autowired
    public ImageIoImageProcessorAdapter(StorageProperties properties) {
        this(properties.maxImageWidth(), properties.maxImageHeight(), properties.maxImagePixels());
    }

    ImageIoImageProcessorAdapter(int maxWidth, int maxHeight, long maxPixels) {
        if (maxWidth <= 0 || maxHeight <= 0 || maxPixels <= 0) {
            throw new IllegalArgumentException("image safety limits must be positive");
        }
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxPixels = maxPixels;
    }

    @Override
    public SanitizedImage sanitizeForStorage(byte[] source, String contentType) {
        if (source == null || source.length == 0 || (!JPEG.equals(contentType) && !PNG.equals(contentType))) {
            throw invalidImage();
        }
        if (PNG.equals(contentType) && containsAnimatedPngChunk(source)) {
            throw new InvalidImageException("Animated images are not allowed");
        }

        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                requireExpectedFormat(reader, contentType);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireSafeDimensions(width, height);

                ImageReadParam readParam = reader.getDefaultReadParam();
                BufferedImage decoded = reader.read(0, readParam);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw invalidImage();
                }

                BufferedImage clean = new BufferedImage(
                        width,
                        height,
                        PNG.equals(contentType) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = clean.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }

                byte[] encoded = encodeWithoutMetadata(clean, contentType);
                clean.flush();
                return new SanitizedImage(encoded, contentType, width, height);
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalidImage();
        }
    }

    @Override
    public Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType) {
        if (source == null || source.length == 0 || targetWidth <= 0) {
            return Optional.empty();
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            if (original == null) {
                return Optional.empty();
            }
            int srcWidth = original.getWidth();
            int srcHeight = original.getHeight();
            if (srcWidth <= targetWidth) {
                return Optional.empty();
            }

            int targetHeight = Math.max(1, (int) Math.round((double) srcHeight * targetWidth / srcWidth));
            boolean png = contentType != null && contentType.toLowerCase().contains("png");
            int imageType = png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, imageType);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
                original.flush();
            }

            byte[] encoded = encodeWithoutMetadata(scaled, png ? PNG : JPEG);
            scaled.flush();
            return Optional.of(encoded);
        } catch (IOException | RuntimeException e) {
            log.warn("Image resize failed; serving original: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private void requireSafeDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight || pixels > maxPixels) {
            throw new InvalidImageException("Image dimensions exceed the allowed limit");
        }
    }

    private static void requireExpectedFormat(ImageReader reader, String contentType) throws IOException {
        String format = reader.getFormatName();
        boolean expected = JPEG.equals(contentType)
                ? "JPEG".equalsIgnoreCase(format) || "JPG".equalsIgnoreCase(format)
                : "PNG".equalsIgnoreCase(format);
        if (!expected) {
            throw invalidImage();
        }
    }

    private static byte[] encodeWithoutMetadata(BufferedImage image, String contentType) throws IOException {
        String format = PNG.equals(contentType) ? "png" : "jpeg";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw invalidImage();
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (JPEG.equals(contentType) && writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(0.9F);
            }
            // stream/image metadata를 모두 null로 전달한다. 새 픽셀 버퍼만 인코딩한다.
            writer.write(null, new IIOImage(image, null, null), writeParam);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static boolean containsAnimatedPngChunk(byte[] source) {
        if (!startsWith(source, PNG_SIGNATURE)) {
            return false;
        }
        int offset = PNG_SIGNATURE.length;
        while (offset <= source.length - 12) {
            long length = readUnsignedInt(source, offset);
            long nextOffset = (long) offset + 12L + length;
            if (length > Integer.MAX_VALUE || nextOffset > source.length) {
                throw invalidImage();
            }
            String type = new String(source, offset + 4, 4, StandardCharsets.US_ASCII);
            if ("acTL".equals(type) || "fcTL".equals(type) || "fdAT".equals(type)) {
                return true;
            }
            offset = (int) nextOffset;
            if ("IEND".equals(type)) {
                return false;
            }
        }
        throw invalidImage();
    }

    private static long readUnsignedInt(byte[] source, int offset) {
        return ((long) (source[offset] & 0xff) << 24)
                | ((long) (source[offset + 1] & 0xff) << 16)
                | ((long) (source[offset + 2] & 0xff) << 8)
                | (source[offset + 3] & 0xffL);
    }

    private static boolean startsWith(byte[] source, byte[] signature) {
        if (source.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (source[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static InvalidImageException invalidImage() {
        return new InvalidImageException("Image could not be safely decoded");
    }
}
