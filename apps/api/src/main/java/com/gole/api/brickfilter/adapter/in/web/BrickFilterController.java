package com.gole.api.brickfilter.adapter.in.web;

import com.gole.api.account.adapter.in.web.AuthenticatedUser;
import com.gole.api.brickfilter.application.port.in.ManageBrickFilterUseCase;
import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/brick-filter")
public class BrickFilterController {
    private final ManageBrickFilterUseCase service;

    public BrickFilterController(ManageBrickFilterUseCase service) {
        this.service = service;
    }

    private String owner(HttpServletRequest request) {
        return AuthenticatedUser.optionalId(request)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
    }

    @GetMapping("/quota")
    public ResponseEntity<ManageBrickFilterUseCase.Quota> quota(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.quota(owner(request)));
    }

    @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ManageBrickFilterUseCase.View> create(
            @RequestHeader("Idempotency-Key") String id,
            @RequestParam Mode mode,
            @RequestParam MultipartFile image,
            HttpServletRequest request)
            throws IOException {
        var user = owner(request);
        if (image.isEmpty() || image.getSize() > 4 * 1024 * 1024)
            throw new BadRequestException("BRICK_IMAGE_INVALID", "PNG/JPEG/HEIF 4MB 이하 사진을 선택해 주세요");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.submit(user, id, mode, image.getBytes()));
    }

    @GetMapping("/jobs")
    public ResponseEntity<java.util.List<ManageBrickFilterUseCase.View>> recent(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.recent(owner(request)));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ManageBrickFilterUseCase.View> job(@PathVariable String id, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(owner(request), id));
    }

    @GetMapping("/jobs/{id}/result")
    public ResponseEntity<byte[]> result(@PathVariable String id, HttpServletRequest request) {
        byte[] bytes = service.result(owner(request), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=brick.png")
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
    }
}
