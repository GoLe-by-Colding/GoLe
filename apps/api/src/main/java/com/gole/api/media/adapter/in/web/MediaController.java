package com.gole.api.media.adapter.in.web;

import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase.LoadedImage;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.StoredImage;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;

/**
 * Inbound 어댑터(REST): 이미지 업로드 및 공개 스트리밍 조회. (요구사항 M1, M2)
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final UploadImageUseCase uploadImageUseCase;
    private final LoadImageUseCase loadImageUseCase;

    public MediaController(UploadImageUseCase uploadImageUseCase, LoadImageUseCase loadImageUseCase) {
        this.uploadImageUseCase = uploadImageUseCase;
        this.loadImageUseCase = loadImageUseCase;
    }

    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Uploaded file is empty");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("Failed to read uploaded file");
        }
        StoredImage stored = uploadImageUseCase.upload(
                new UploadImageCommand(content, file.getContentType(), file.getOriginalFilename()));
        return new UploadResponse(stored.key(), stored.url());
    }

    /** 키에 슬래시가 포함되므로 나머지 경로 전체를 키로 캡처한다. */
    @GetMapping("/images/{*key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        LoadedImage image = loadImageUseCase.load(normalizedKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image.content());
    }

    public record UploadResponse(String key, String url) {}
}
