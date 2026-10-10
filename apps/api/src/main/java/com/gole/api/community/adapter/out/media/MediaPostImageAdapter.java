package com.gole.api.community.adapter.out.media;

import com.gole.api.community.application.port.out.PostImagePort;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaTargetType;
import java.util.List;
import org.springframework.stereotype.Component;

/** 미디어 컨텍스트 통합 어댑터. 게시글 이미지를 media 의 수명주기 유스케이스에 맡긴다. */
@Component
public class MediaPostImageAdapter implements PostImagePort {

    private final ManageMediaAssetsUseCase mediaAssets;

    public MediaPostImageAdapter(ManageMediaAssetsUseCase mediaAssets) {
        this.mediaAssets = mediaAssets;
    }

    @Override
    public void replaceImages(String ownerId, String postId, List<String> imageKeys, boolean publiclyVisible) {
        mediaAssets.replaceReferences(ownerId, MediaTargetType.COMMUNITY_POST, postId, imageKeys, publiclyVisible);
    }

    @Override
    public void setVisibility(String postId, boolean publiclyVisible) {
        mediaAssets.setTargetVisibility(MediaTargetType.COMMUNITY_POST, postId, publiclyVisible);
    }

    @Override
    public void revokeImages(String postId) {
        mediaAssets.revokeTarget(MediaTargetType.COMMUNITY_POST, postId);
    }
}
