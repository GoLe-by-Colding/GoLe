package com.gole.api.design.adapter.out.media;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.design.application.port.out.MascotMediaPort;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaKey;
import com.gole.api.media.domain.model.MediaTargetType;
import java.util.List;
import org.springframework.stereotype.Component;

/** 미디어 컨텍스트 통합 어댑터. 마스코트 이미지의 공개 연결·회수와 공개 경로를 media 에 맡긴다. */
@Component
public class MediaMascotAdapter implements MascotMediaPort {

    private final ManageMediaAssetsUseCase mediaAssets;

    public MediaMascotAdapter(ManageMediaAssetsUseCase mediaAssets) {
        this.mediaAssets = mediaAssets;
    }

    @Override
    public String publicPath(String mediaKey) {
        if (!MediaKey.isUserKey(mediaKey)) {
            throw new BadRequestException("MASCOT_IMAGE_REQUIRED", "업로드한 이미지 키가 아닙니다");
        }
        return MediaKey.publicPath(mediaKey);
    }

    @Override
    public void attach(String ownerId, String assetId, List<String> mediaKeys) {
        mediaAssets.replaceReferences(ownerId, MediaTargetType.MASCOT_ASSET, assetId, mediaKeys, true);
    }

    @Override
    public void release(String assetId) {
        mediaAssets.revokeTarget(MediaTargetType.MASCOT_ASSET, assetId);
    }
}
