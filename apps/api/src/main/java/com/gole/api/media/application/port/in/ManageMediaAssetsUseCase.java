package com.gole.api.media.application.port.in;

import com.gole.api.media.domain.model.MediaTargetType;
import java.util.List;

/** 업로드 등록과 콘텐츠 참조 전이를 수행하는 미디어 수명주기 유스케이스. */
public interface ManageMediaAssetsUseCase {

    void registerStaged(String ownerId, String key, String contentType, long size);

    /** 요청 목록이 대상의 전체 미디어 목록이며, 빠진 기존 참조는 즉시 폐기한다. */
    void replaceReferences(
            String ownerId,
            MediaTargetType targetType,
            String targetId,
            List<String> requestedKeys,
            boolean publiclyVisible);

    /** 사진 목록은 그대로 두고 draft/published 전환만 원장에 반영한다. */
    void setTargetVisibility(MediaTargetType targetType, String targetId, boolean publiclyVisible);

    void revokeTarget(MediaTargetType targetType, String targetId);

    /** revoke와 경합한 파생물 보상 삭제가 실패했을 때 삭제 journal을 다시 PENDING으로 만든다. */
    void requeueDeletion(String key);
}
