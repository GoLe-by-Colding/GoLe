import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

package com.gole.api.media.adapter.in.web;

import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase.LoadedImage;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.StoredImage;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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

/**
 * Inbound 어댑터(REST): 이미지 업로드 및 공개 스트리밍 조회. (요구사항 M1, M2)
 */
@Tag(name = "Media", description = "이미지 업로드(MinIO)·스트리밍 조회")
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    /** 배치 업로드 1회 최대 파일 수. */
    private static final int MAX_BATCH_SIZE = 10;

    private final UploadImageUseCase uploadImageUseCase;
    private final LoadImageUseCase loadImageUseCase;

    public MediaController(UploadImageUseCase uploadImageUseCase, LoadImageUseCase loadImageUseCase) {
        this.uploadImageUseCase = uploadImageUseCase;
        this.loadImageUseCase = loadImageUseCase;
    }

    @Operation(summary = "이미지 단일 업로드", description = "이미지 파일을 MinIO에 업로드하고 공개 URL을 반환합니다. Content-Type: multipart/form-data")
    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return storeOne(file);
    }

    /** 다중 이미지 업로드. 매물 사진 등 여러 장을 한 번에 올린다. (요구사항 M1, N2) */
    @PostMapping("/images/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<UploadResponse> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidImageException("No files were uploaded");
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new InvalidImageException("Too many files: max " + MAX_BATCH_SIZE + " per request");
        }
        return files.stream().map(this::storeOne).toList();
    }

    private UploadResponse storeOne(MultipartFile file) {
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

    /** 키에 슬래시가 포함되므로(예: {@code images/<uuid>.png}) 나머지 경로 전체를 키로 캡처한다.
     *  {@code ?w=240} 지정 시 해당 폭 썸네일을 제공한다(없거나 불가 시 원본). (백로그 N2a) */
    @GetMapping("/{*key}")
    public ResponseEntity<byte[]> get(
            @PathVariable String key, @RequestParam(value = "w", required = false) Integer w) {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        LoadedImage image =
                (w == null) ? loadImageUseCase.load(normalizedKey) : loadImageUseCase.loadResized(normalizedKey, w);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image.content());
    }

    public record UploadResponse(String key, String url) {}
}
