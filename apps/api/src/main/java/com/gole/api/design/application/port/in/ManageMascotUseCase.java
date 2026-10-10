package com.gole.api.design.application.port.in;

import com.gole.api.design.domain.model.ActiveMascot;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotSelection;
import java.util.List;

/** 사이트 마스코트를 고르고 새 그림을 올리는 관리자 유스케이스. (mascot-assets) */
public interface ManageMascotUseCase {

    /** 사이트가 지금 그릴 마스코트. 가리키는 에셋이 없으면 기본 고래다. */
    ActiveMascot active();

    MascotSelection currentSelection();

    /** 업로드 에셋, 최근 것부터. */
    List<MascotAsset> uploads();

    /** {@code before}보다 작은 리비전을 최근 것부터 25개. */
    List<MascotSelection> history(long before);

    MascotAsset register(RegisterMascotCommand command);

    MascotSelection publish(long expectedRevision, String assetId, String reason, String actorId);

    /** 업로드 에셋을 지우고 이미지를 회수한다. 적용 중이면 거부한다. */
    void delete(String assetId, String actorId);

    /**
     * @param imageKey     {@code POST /api/v1/media/images}로 먼저 올린 저장 키
     * @param darkImageKey 어두운 배경용 저장 키(선택, null 가능)
     */
    record RegisterMascotCommand(
            String name,
            String description,
            String imageKey,
            String darkImageKey,
            int width,
            int height,
            String actorId) {}
}
