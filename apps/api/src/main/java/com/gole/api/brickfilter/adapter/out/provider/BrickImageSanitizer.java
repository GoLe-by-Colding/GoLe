package com.gole.api.brickfilter.adapter.out.provider;

import com.gole.api.brickfilter.application.port.out.BrickImagePort;
import com.gole.api.common.exception.BadRequestException;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class BrickImageSanitizer implements BrickImagePort {
    private final com.gole.api.media.application.port.in.NormalizeImageUseCase media;

    public BrickImageSanitizer(com.gole.api.media.application.port.in.NormalizeImageUseCase media) {
        this.media = media;
    }

    public byte[] sanitize(byte[] input, boolean result) {
        int limit = (result ? 8 : 4) * 1024 * 1024;
        if (input == null || input.length == 0 || input.length > limit) throw invalid();
        if (!result) {
            var normalized = media.normalize(input);
            if (normalized.width() > 6000
                    || normalized.height() > 6000
                    || (long) normalized.width() * normalized.height() > 12_000_000) throw invalid();
            input = normalized.content();
        }
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                if (!java.util.Set.of("png", "jpeg")
                        .contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw invalid();
                reader.setInput(stream);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 6000 || h > 6000 || (long) w * h > 12_000_000) throw invalid();
                if (reader.getNumImages(true) != 1) throw invalid();
                var pixels = reader.read(0);
                if (!result && Math.max(w, h) > 1024) {
                    double scale = 1024.0 / Math.max(w, h);
                    var small = new java.awt.image.BufferedImage(
                            Math.max(1, (int) (w * scale)),
                            Math.max(1, (int) (h * scale)),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = small.createGraphics();
                    try {
                        graphics.setRenderingHint(
                                java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(pixels, 0, 0, small.getWidth(), small.getHeight(), null);
                    } finally {
                        graphics.dispose();
                        pixels.flush();
                    }
                    pixels = small;
                }
                var out = new ByteArrayOutputStream();
                ImageIO.write(pixels, "png", out);
                if (out.size() > limit) throw invalid();
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("BRICK_IMAGE_INVALID", "PNG/JPEG/HEIF 4MB 이하, 1200만 화소 이하 사진을 선택해 주세요");
    }
}
