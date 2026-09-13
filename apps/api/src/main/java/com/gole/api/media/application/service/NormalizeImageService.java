package com.gole.api.media.application.service;

import com.gole.api.media.application.port.in.NormalizeImageUseCase;
import com.gole.api.media.application.port.out.ImageProcessorPort;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.HeifSignature;
import org.springframework.stereotype.Service;

@Service
public class NormalizeImageService implements NormalizeImageUseCase {
    private final ImageProcessorPort processor;

    public NormalizeImageService(ImageProcessorPort processor) {
        this.processor = processor;
    }

    @Override
    public NormalizedImage normalize(byte[] source) {
        if (source == null || source.length == 0) throw invalid();
        String mime;
        if (HeifSignature.matches(source)) mime = "image/heic";
        else if (source.length >= 8
                && source[0] == (byte) 0x89
                && source[1] == 0x50
                && source[2] == 0x4e
                && source[3] == 0x47) mime = "image/png";
        else if (source.length >= 3 && source[0] == (byte) 0xff && source[1] == (byte) 0xd8 && source[2] == (byte) 0xff)
            mime = "image/jpeg";
        else throw invalid();
        var image = processor.sanitizeForStorage(source, mime);
        return new NormalizedImage(image.content(), image.contentType(), image.width(), image.height());
    }

    private static InvalidImageException invalid() {
        return new InvalidImageException("JPEG/PNG 또는 HEIC/HEIF 정지 사진만 처리할 수 있습니다");
    }
}
