package com.gole.api.listing.adapter.out.media;

import com.gole.api.listing.application.port.out.ListingPhotoPort;
import com.gole.api.media.application.port.in.ManageMediaAssetsUseCase;
import com.gole.api.media.domain.model.MediaTargetType;
import java.util.List;
import org.springframework.stereotype.Component;

/** 미디어 컨텍스트 통합 어댑터. 매물 사진을 media 의 수명주기 유스케이스에 맡긴다. */
@Component
public class MediaListingPhotoAdapter implements ListingPhotoPort {

    private final ManageMediaAssetsUseCase mediaAssets;

    public MediaListingPhotoAdapter(ManageMediaAssetsUseCase mediaAssets) {
        this.mediaAssets = mediaAssets;
    }

    @Override
    public void replacePhotos(String sellerId, String listingId, List<String> photoKeys) {
        mediaAssets.replaceReferences(sellerId, MediaTargetType.LISTING, listingId, photoKeys, true);
    }

    @Override
    public void revokePhotos(String listingId) {
        mediaAssets.revokeTarget(MediaTargetType.LISTING, listingId);
    }
}
