package com.gole.api.promotion.adapter.out.media;

import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaKey;
import com.gole.api.media.domain.model.MediaTargetType;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import java.util.List;
import org.springframework.stereotype.Component;

/** 미디어 컨텍스트 통합 어댑터. 홍보 초안 미디어의 공개 경로와 연결을 media 에 맡긴다. */
@Component
public class MediaPromotionMediaAdapter implements PromotionMediaPort {

    private final ManageMediaAssetsUseCase mediaAssets;

    public MediaPromotionMediaAdapter(ManageMediaAssetsUseCase mediaAssets) {
        this.mediaAssets = mediaAssets;
    }

    @Override
    public String publicPath(String mediaKey) {
        return MediaKey.publicPath(mediaKey);
    }

    @Override
    public void attachToPost(String authorId, String postId, List<String> mediaKeys) {
        mediaAssets.replaceReferences(authorId, MediaTargetType.PROMOTION_POST, postId, mediaKeys, true);
    }
}
