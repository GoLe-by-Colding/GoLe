package com.gole.api.media.adapter.in.web;

import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.media.application.port.in.AcquireMediaUploadQuotaUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase;
import com.gole.api.media.application.port.in.LoadImageUseCase.LoadedImage;
import com.gole.api.media.application.port.in.UploadImageUseCase;
import com.gole.api.media.application.port.in.UploadImageUseCase.UploadImageCommand;
import com.gole.api.media.domain.exception.InvalidImageException;
import com.gole.api.media.domain.model.StoredImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
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
    private final AcquireMediaUploadQuotaUseCase uploadQuota;

    public MediaController(
            UploadImageUseCase uploadImageUseCase,
            LoadImageUseCase loadImageUseCase,
            AcquireMediaUploadQuotaUseCase uploadQuota) {
        this.uploadImageUseCase = uploadImageUseCase;
        this.loadImageUseCase = loadImageUseCase;
        this.uploadQuota = uploadQuota;
    }

    @Operation(
            summary = "이미지 단일 업로드",
            description = "JPEG/PNG 정지 이미지를 메타데이터 없이 재인코딩해 저장하고 소유자 미리보기 URL을 반환합니다. Content-Type: multipart/form-data")
    @PostMapping("/images")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        uploadQuota.acquire(AuthenticatedUser.id(request), 1);
        return storeOne(file, AuthenticatedUser.id(request));
    }

    /** 다중 이미지 업로드. 매물 사진 등 여러 장을 한 번에 올린다. (요구사항 M1, N2) */
    @PostMapping("/images/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<UploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files, HttpServletRequest request) {
        if (files == null || files.isEmpty()) {
            throw new InvalidImageException("올린 파일이 없습니다");
        }
        if (files.size() > MAX_BATCH_SIZE) {
            throw new InvalidImageException("한 번에 " + MAX_BATCH_SIZE + "장까지 올릴 수 있습니다");
        }
        uploadQuota.acquire(AuthenticatedUser.id(request), files.size());
        String ownerId = AuthenticatedUser.id(request);
        return files.stream().map(file -> storeOne(file, ownerId)).toList();
    }

    private UploadResponse storeOne(MultipartFile file, String ownerId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("빈 파일은 올릴 수 없습니다");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("올린 파일을 읽지 못했습니다. 다시 시도해 주세요");
        }
        StoredImage stored = uploadImageUseCase.upload(
                new UploadImageCommand(ownerId, content, file.getContentType(), file.getOriginalFilename()));
        return new UploadResponse(stored.key(), stored.url());
    }

    /** 키에 슬래시가 포함되므로(예: {@code images/<uuid>.png}) 나머지 경로 전체를 키로 캡처한다.
     *  {@code ?w=240} 지정 시 해당 폭 썸네일을 제공한다(없거나 불가 시 원본). (백로그 N2a) */
    @GetMapping("/{*key}")
    public ResponseEntity<byte[]> get(
            @PathVariable String key,
            @RequestParam(value = "w", required = false) Integer w,
            HttpServletRequest request) {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        java.util.Optional<String> viewerId = AuthenticatedUser.optionalId(request);
        LoadedImage image = (w == null)
                ? loadImageUseCase.load(normalizedKey, viewerId)
                : loadImageUseCase.loadResized(normalizedKey, w, viewerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                // 폐기 직후 브라우저·공유 캐시에서도 재노출되지 않도록 사용자 미디어를 저장하지 않는다.
                .cacheControl(CacheControl.noStore())
                // 같은 출처에서 SVG도 내보낸다. 주소를 직접 열어도 내용을 추측해 HTML로 읽거나 스크립트를 실행하지 않게
                // 한다. <img>로 넣은 그림에는 이 응답의 CSP가 적용되지 않아 화면은 그대로다.
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(image.content());
    }

    public record UploadResponse(String key, String url) {}
}
