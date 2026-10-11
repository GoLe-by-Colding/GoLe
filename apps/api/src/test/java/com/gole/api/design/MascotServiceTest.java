package com.gole.api.design;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.design.application.port.in.ManageMascotUseCase.RegisterMascotCommand;
import com.gole.api.design.application.port.out.MascotMediaPort;
import com.gole.api.design.application.port.out.MascotRepositoryPort;
import com.gole.api.design.application.service.MascotService;
import com.gole.api.design.domain.model.ActiveMascot;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotPresets;
import com.gole.api.design.domain.model.MascotSelection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MascotServiceTest {
    static final String KEY = "images/11111111-1111-4111-8111-111111111111.png";
    static final String DARK_KEY = "images/22222222-2222-4222-8222-222222222222.png";
    static final Instant NOW = Instant.parse("2026-10-10T12:00:00.123456Z");

    final MascotRepositoryPort repository = mock(MascotRepositoryPort.class);
    final MascotMediaPort media = mock(MascotMediaPort.class);
    final MascotService service = new MascotService(repository, media, Clock.fixed(NOW, ZoneOffset.UTC));

    static MascotAsset upload(String id) {
        return new MascotAsset(
                id, "새 고래", "", KEY, "/api/v1/media/" + KEY, null, null, 400, 300, "admin-1", Instant.EPOCH);
    }

    @Test
    @DisplayName("기본 제공 마스코트는 12벌이고 기본값은 옆모습 브릭 고래다")
    void presets_listTwelveWithSideBrickDefault() {
        assertThat(MascotPresets.KEYS).hasSize(12).first().isEqualTo("side-brick");
        assertThat(MascotPresets.KEYS).doesNotHaveDuplicates();
        assertThat(MascotSelection.initial().assetId()).isEqualTo(MascotPresets.DEFAULT_KEY);
    }

    @Test
    @DisplayName("적용은 다음 리비전을 쌓고 이름 스냅숏·조치자·다듬은 사유를 함께 남긴다")
    void publish_appendsNextRevisionWithAudit() {
        when(repository.currentSelection()).thenReturn(MascotSelection.initial());

        var next = service.publish(0, "baby-round", "  가을 이벤트  ", "admin-1");

        assertThat(next.revision()).isEqualTo(1);
        assertThat(next.assetName()).isEqualTo("둥근 아기 고래");
        assertThat(next.actorId()).isEqualTo("admin-1");
        assertThat(next.reason()).isEqualTo("가을 이벤트");
        assertThat(next.publishedAt()).isEqualTo(Instant.parse("2026-10-10T12:00:00.123Z"));
        verify(repository).appendSelection(next);
    }

    @Test
    @DisplayName("기대 리비전이 다르면 충돌, 이미 적용 중이거나 없는 에셋이면 거부한다")
    void publish_rejectsStaleSameAndUnknown() {
        when(repository.currentSelection()).thenReturn(MascotSelection.initial());
        when(repository.findAsset("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(3, "baby-round", "reason", "admin"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.publish(0, "side-brick", "reason", "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 적용 중");
        assertThatThrownBy(() -> service.publish(0, "gone", "reason", "admin")).isInstanceOf(BadRequestException.class);
        verify(repository, never()).appendSelection(any());
    }

    @Test
    @DisplayName("조치자와 1~300자 사유가 없으면 적용하지 않는다")
    void publish_requiresActorAndReason() {
        assertThatThrownBy(() -> service.publish(0, "baby-round", "reason", " "))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.publish(0, "baby-round", " ", "admin"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.publish(0, "baby-round", "x".repeat(301), "admin"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("업로드 등록은 키를 올린 관리자 본인으로 공개 연결하고 공개 경로를 키에서 만든다")
    void register_attachesKeysAndStoresPublicPaths() {
        when(media.publicPath(KEY)).thenReturn("/api/v1/media/" + KEY);
        when(media.publicPath(DARK_KEY)).thenReturn("/api/v1/media/" + DARK_KEY);

        var asset =
                service.register(new RegisterMascotCommand(" 축제 고래 ", " 10월 한정 ", KEY, DARK_KEY, 640, 360, "admin-1"));

        assertThat(asset.name()).isEqualTo("축제 고래");
        assertThat(asset.description()).isEqualTo("10월 한정");
        assertThat(asset.imageUrl()).isEqualTo("/api/v1/media/" + KEY);
        assertThat(asset.darkImageUrl()).isEqualTo("/api/v1/media/" + DARK_KEY);
        assertThat(asset.createdBy()).isEqualTo("admin-1");
        verify(media).attach("admin-1", asset.id(), List.of(KEY, DARK_KEY));
        verify(repository).saveAsset(asset);
    }

    @Test
    @DisplayName("어두운 배경용 키가 비어 있으면 기본 이미지 하나만 연결한다")
    void register_withoutDarkImage() {
        when(media.publicPath(KEY)).thenReturn("/api/v1/media/" + KEY);

        var asset = service.register(new RegisterMascotCommand("고래", null, KEY, " ", 64, 64, "admin-1"));

        assertThat(asset.darkImageKey()).isNull();
        assertThat(asset.darkImageUrl()).isNull();
        verify(media).attach(eq("admin-1"), eq(asset.id()), eq(List.of(KEY)));
    }

    @Test
    @DisplayName("입력이 틀리거나 50개가 차면 미디어를 건드리기 전에 거부한다")
    void register_validatesBeforeTouchingMedia() {
        assertThatThrownBy(() -> service.register(new RegisterMascotCommand(" ", "", KEY, null, 64, 64, "a")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.register(new RegisterMascotCommand("고래", "", KEY, null, 8, 64, "a")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.register(new RegisterMascotCommand("고래", "", KEY, KEY, 64, 64, "a")))
                .isInstanceOf(BadRequestException.class);
        when(repository.countAssets()).thenReturn(50L);
        assertThatThrownBy(() -> service.register(new RegisterMascotCommand("고래", "", KEY, null, 64, 64, "a")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(media);
        verify(repository, never()).saveAsset(any());
    }

    @Test
    @DisplayName("적용 중인 에셋과 기본 제공 에셋은 지울 수 없고, 지우면 이미지도 회수한다")
    void delete_guardsActiveAndPresets() {
        when(repository.currentSelection())
                .thenReturn(new MascotSelection(4, "active-id", "고래", "a", "r", "PUBLISH", Instant.EPOCH));

        assertThatThrownBy(() -> service.delete("side-brick", "admin")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.delete("active-id", "admin")).isInstanceOf(ConflictException.class);
        verify(repository, never()).deleteAsset(any());

        when(repository.deleteAsset("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.delete("missing", "admin")).isInstanceOf(NotFoundException.class);
        verify(media, never()).release(any());

        when(repository.deleteAsset("old-id")).thenReturn(true);
        service.delete("old-id", "admin");
        verify(media).release("old-id");
    }

    @Test
    @DisplayName("공개 마스코트는 프리셋·업로드를 구분하고, 사라진 업로드를 가리키면 기본 고래로 떨어진다")
    void active_resolvesPresetUploadAndFallback() {
        when(repository.currentSelection())
                .thenReturn(new MascotSelection(2, "tail-up", "꼬리 든 고래", "a", "r", "PUBLISH", Instant.EPOCH));
        assertThat(service.active()).isEqualTo(ActiveMascot.preset(2, "tail-up", "꼬리 든 고래"));

        var uploaded = upload("u-1");
        when(repository.currentSelection())
                .thenReturn(new MascotSelection(3, "u-1", "새 고래", "a", "r", "PUBLISH", Instant.EPOCH));
        when(repository.findAsset("u-1")).thenReturn(Optional.of(uploaded));
        var active = service.active();
        assertThat(active.kind()).isEqualTo(ActiveMascot.Kind.UPLOAD);
        assertThat(active.imageUrl()).isEqualTo("/api/v1/media/" + KEY);
        assertThat(active.width()).isEqualTo(400);

        when(repository.findAsset("u-1")).thenReturn(Optional.empty());
        assertThat(service.active()).isEqualTo(ActiveMascot.preset(3, "side-brick", "옆모습 브릭 고래"));
    }

    @Test
    @DisplayName("업로드 등록이 미디어 원장 검증에 걸리면 저장하지 않는다")
    void register_doesNotSaveWhenMediaRejects() {
        doThrow(new BadRequestException("INVALID_MEDIA_REFERENCE", "bad"))
                .when(media)
                .attach(any(), any(), any());

        assertThatThrownBy(() -> service.register(new RegisterMascotCommand("고래", "", KEY, null, 64, 64, "a")))
                .isInstanceOf(BadRequestException.class);
        ArgumentCaptor<MascotAsset> saved = ArgumentCaptor.forClass(MascotAsset.class);
        verify(repository, never()).saveAsset(saved.capture());
    }
}
