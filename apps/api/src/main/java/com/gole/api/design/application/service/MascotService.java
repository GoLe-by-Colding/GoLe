package com.gole.api.design.application.service;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.design.application.port.in.ManageMascotUseCase;
import com.gole.api.design.application.port.out.MascotMediaPort;
import com.gole.api.design.application.port.out.MascotRepositoryPort;
import com.gole.api.design.domain.model.ActiveMascot;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotPresets;
import com.gole.api.design.domain.model.MascotSelection;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마스코트 고르기·올리기·지우기. (mascot-assets R1~R4)
 *
 * <p>적용은 디자인 토큰처럼 리비전을 하나씩 쌓는다. 리비전 문서가 곧 감사 기록이라 적용과 기록이 갈라지지 않고,
 * 같은 기대 리비전의 동시 적용은 저장소의 unique {@code _id}가 한 건만 통과시킨다.
 */
@Service
public class MascotService implements ManageMascotUseCase {

    /** 업로드 에셋 상한. 목록 화면과 미디어 보관량을 묶어 둔다. */
    static final int MAX_UPLOADS = 50;

    static final int REASON_MAX = 300;

    private final MascotRepositoryPort repository;
    private final MascotMediaPort media;
    private final Clock clock;

    public MascotService(MascotRepositoryPort repository, MascotMediaPort media, Clock clock) {
        this.repository = repository;
        this.media = media;
        this.clock = clock;
    }

    @Override
    public ActiveMascot active() {
        MascotSelection selection = repository.currentSelection();
        if (MascotPresets.isPreset(selection.assetId())) {
            return ActiveMascot.preset(
                    selection.revision(),
                    selection.assetId(),
                    MascotPresets.nameOf(selection.assetId()).orElse(selection.assetName()));
        }
        // 적용과 삭제가 겹쳐 사라진 에셋을 가리켜도 화면이 비지 않게 기본 고래로 떨어진다.
        return repository
                .findAsset(selection.assetId())
                .map(asset -> ActiveMascot.upload(selection.revision(), asset))
                .orElseGet(() -> ActiveMascot.preset(
                        selection.revision(), MascotPresets.DEFAULT_KEY, MascotPresets.DEFAULT_NAME));
    }

    @Override
    public MascotSelection currentSelection() {
        return repository.currentSelection();
    }

    @Override
    public List<MascotAsset> uploads() {
        return repository.assets();
    }

    @Override
    public List<MascotSelection> history(long before) {
        return repository.history(before);
    }

    @Override
    @Transactional
    public MascotAsset register(RegisterMascotCommand command) {
        requireActor(command.actorId());
        String darkImageKey =
                command.darkImageKey() == null || command.darkImageKey().isBlank() ? null : command.darkImageKey();
        MascotAsset.validateInput(
                command.name(),
                command.description(),
                command.imageKey(),
                darkImageKey,
                command.width(),
                command.height());
        if (repository.countAssets() >= MAX_UPLOADS) {
            throw new ConflictException(
                    "MASCOT_ASSET_LIMIT", "업로드 마스코트는 " + MAX_UPLOADS + "개까지입니다. 쓰지 않는 것을 지운 뒤 올려 주세요");
        }
        String id = UUID.randomUUID().toString();
        List<String> keys = new ArrayList<>();
        keys.add(command.imageKey());
        if (darkImageKey != null) {
            keys.add(darkImageKey);
        }
        // 키 형식·소유자·staged 여부는 미디어 원장이 판정한다. 남의 업로드나 이미 쓰인 키는 여기서 거부된다.
        media.attach(command.actorId(), id, keys);
        MascotAsset asset = MascotAsset.register(
                id,
                command.name(),
                command.description(),
                command.imageKey(),
                media.publicPath(command.imageKey()),
                darkImageKey,
                darkImageKey == null ? null : media.publicPath(darkImageKey),
                command.width(),
                command.height(),
                command.actorId(),
                now());
        repository.saveAsset(asset);
        return asset;
    }

    @Override
    public MascotSelection publish(long expectedRevision, String assetId, String reason, String actorId) {
        requireActor(actorId);
        if (reason == null || reason.isBlank() || reason.strip().length() > REASON_MAX) {
            throw new BadRequestException("MASCOT_REASON_REQUIRED", "변경 사유를 1~" + REASON_MAX + "자로 입력해 주세요");
        }
        String assetName = nameOf(assetId);
        MascotSelection current = repository.currentSelection();
        if (expectedRevision < 0 || expectedRevision != current.revision()) {
            throw new ConflictException("MASCOT_REVISION_CONFLICT", "다른 관리자가 먼저 바꿨습니다. 최신 값을 불러온 뒤 다시 검토해 주세요");
        }
        if (assetId.equals(current.assetId())) {
            throw new BadRequestException("MASCOT_ALREADY_ACTIVE", "이미 적용 중인 마스코트입니다");
        }
        MascotSelection next = new MascotSelection(
                Math.addExact(expectedRevision, 1), assetId, assetName, actorId, reason.strip(), "PUBLISH", now());
        repository.appendSelection(next);
        return next;
    }

    @Override
    @Transactional
    public void delete(String assetId, String actorId) {
        requireActor(actorId);
        if (MascotPresets.isPreset(assetId)) {
            throw new BadRequestException("MASCOT_PRESET_READONLY", "기본 제공 마스코트는 지울 수 없습니다");
        }
        if (assetId != null && assetId.equals(repository.currentSelection().assetId())) {
            throw new ConflictException("MASCOT_ASSET_ACTIVE", "적용 중인 마스코트는 지울 수 없습니다. 다른 마스코트를 먼저 적용해 주세요");
        }
        if (!repository.deleteAsset(assetId)) {
            throw new NotFoundException("MASCOT_ASSET_NOT_FOUND", "마스코트를 찾을 수 없습니다");
        }
        media.release(assetId);
    }

    private String nameOf(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            throw new BadRequestException("MASCOT_ASSET_NOT_FOUND", "적용할 마스코트를 골라 주세요");
        }
        if (MascotPresets.isPreset(assetId)) {
            return MascotPresets.nameOf(assetId).orElseThrow();
        }
        return repository
                .findAsset(assetId)
                .map(MascotAsset::name)
                .orElseThrow(() -> new BadRequestException("MASCOT_ASSET_NOT_FOUND", "지웠거나 없는 마스코트입니다"));
    }

    private Instant now() {
        // Mongo가 밀리초까지만 저장하므로 저장 전후 값이 같도록 맞춘다.
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
    }
}
