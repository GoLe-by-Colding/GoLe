package com.gole.api.media.adapter.out.image;

import com.gole.api.media.application.port.out.ImageProcessorPort;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JDK {@link ImageIO} 기반 이미지 리사이즈 어댑터. 추가 의존성 없이 동작한다.
 * (이미지 썸네일 — 백로그 N2a)
 *
 * <p>디코딩 불가 포맷(예: 기본 JDK가 못 읽는 webp)이거나 원본이 목표보다 작으면
 * 빈 Optional을 반환해 호출부가 원본을 그대로 제공하게 한다.
 */
@Component
public class ImageIoImageProcessorAdapter implements ImageProcessorPort {

    private static final Logger log = LoggerFactory.getLogger(ImageIoImageProcessorAdapter.class);

    @Override
    public Optional<byte[]> resizeToWidth(byte[] source, int targetWidth, String contentType) {
        if (source == null || source.length == 0 || targetWidth <= 0) {
            return Optional.empty();
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            if (original == null) {
                return Optional.empty(); // 미지원 포맷 → 원본 사용
            }
            int srcWidth = original.getWidth();
            int srcHeight = original.getHeight();
            if (srcWidth <= targetWidth) {
                return Optional.empty(); // 업스케일 안 함 → 원본 사용
            }

            int targetHeight = Math.max(1, (int) Math.round((double) srcHeight * targetWidth / srcWidth));
            boolean png = contentType != null && contentType.toLowerCase().contains("png");
            int imageType = png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, imageType);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            String format = png ? "png" : "jpeg";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, format, out)) {
                return Optional.empty();
            }
            return Optional.of(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.warn("Image resize failed; serving original: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
