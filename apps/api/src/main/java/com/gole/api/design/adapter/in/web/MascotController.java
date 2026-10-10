package com.gole.api.design.adapter.in.web;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.common.web.auth.AdminActor;
import com.gole.api.design.application.port.in.ManageMascotUseCase;
import com.gole.api.design.application.port.in.ManageMascotUseCase.RegisterMascotCommand;
import com.gole.api.design.domain.model.ActiveMascot;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotPresets;
import com.gole.api.design.domain.model.MascotSelection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이트 마스코트 공개 조회와 관리자 관리. (mascot-assets)
 *
 * <p>{@code /api/admin/**}는 기존 AdminWebConfig 가 서버 세션의 ADMIN 역할로 막는다. 조치자는 요청 속성에서만
 * 읽고 본문에서 받지 않는다. 등록·적용·삭제가 성공한 뒤에만 중앙 감사 로그에 남긴다.
 */
@RestController
public class MascotController {
    private final ManageMascotUseCase mascots;
    private final RecordAdminActionUseCase audit;

    public MascotController(ManageMascotUseCase mascots, RecordAdminActionUseCase audit) {
        this.mascots = mascots;
        this.audit = audit;
    }

    public record PublicMascot(long revision, Asset asset) {
        public record Asset(
                String id,
                ActiveMascot.Kind kind,
                String name,
                String imageUrl,
                String darkImageUrl,
                Integer width,
                Integer height) {}

        static PublicMascot of(ActiveMascot m) {
            return new PublicMascot(
                    m.revision(),
                    new Asset(m.id(), m.kind(), m.name(), m.imageUrl(), m.darkImageUrl(), m.width(), m.height()));
        }
    }

    public record Editor(
            MascotSelection current, PublicMascot active, List<String> presets, List<MascotAsset> uploads) {}

    public record Register(
            @NotBlank @Size(max = 40) String name,
            @Size(max = 200) String description,
            @NotBlank @Size(max = 200) String imageKey,
            @Size(max = 200) String darkImageKey,
            @Min(16) @Max(4096) int width,
            @Min(16) @Max(4096) int height) {}

    public record Publish(
            @Min(0) long expectedRevision,
            @NotBlank @Size(max = 64) String assetId,
            @NotBlank @Size(max = 300) String reason) {}

    @GetMapping("/api/v1/config/mascot")
    public ResponseEntity<PublicMascot> active() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PublicMascot.of(mascots.active()));
    }

    @GetMapping("/api/admin/mascot")
    public Editor editor() {
        return new Editor(
                mascots.currentSelection(), PublicMascot.of(mascots.active()), MascotPresets.KEYS, mascots.uploads());
    }

    @GetMapping("/api/admin/mascot/history")
    public List<MascotSelection> history(@RequestParam(defaultValue = "9223372036854775807") long before) {
        return mascots.history(before);
    }

    @PostMapping("/api/admin/mascot/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public MascotAsset register(@Valid @RequestBody Register body, HttpServletRequest request) {
        AdminActor actor = AdminActor.of(request);
        MascotAsset asset = mascots.register(new RegisterMascotCommand(
                body.name(),
                body.description(),
                body.imageKey(),
                body.darkImageKey(),
                body.width(),
                body.height(),
                actor.id()));
        record(actor, AdminActionType.MASCOT_ASSET_CREATE, asset.id(), asset.name());
        return asset;
    }

    @PostMapping("/api/admin/mascot/publish")
    public MascotSelection publish(@Valid @RequestBody Publish body, HttpServletRequest request) {
        AdminActor actor = AdminActor.of(request);
        MascotSelection next = mascots.publish(body.expectedRevision(), body.assetId(), body.reason(), actor.id());
        record(actor, AdminActionType.MASCOT_PUBLISH, next.assetId(), next.reason());
        return next;
    }

    @DeleteMapping("/api/admin/mascot/assets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpServletRequest request) {
        AdminActor actor = AdminActor.of(request);
        mascots.delete(id, actor.id());
        record(actor, AdminActionType.MASCOT_ASSET_DELETE, id, null);
    }

    private void record(AdminActor actor, AdminActionType type, String assetId, String reason) {
        audit.record(new RecordAdminActionCommand(
                actor.id(), actor.email(), type, AdminTargetType.MASCOT_ASSET, assetId, reason));
    }
}
