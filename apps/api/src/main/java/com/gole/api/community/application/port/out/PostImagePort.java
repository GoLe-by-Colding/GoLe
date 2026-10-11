package com.gole.api.community.application.port.out;

import java.util.List;

/** Outbound port: 게시글 이미지의 미디어 수명주기(참조 교체·공개 전환·폐기). */
public interface PostImagePort {

    /** 요청 목록이 게시글의 전체 이미지다. 빠진 기존 참조는 즉시 폐기한다. */
    void replaceImages(String ownerId, String postId, List<String> imageKeys, boolean publiclyVisible);

    /** 이미지 목록은 그대로 두고 초안·게시 전환만 반영한다. */
    void setVisibility(String postId, boolean publiclyVisible);

    void revokeImages(String postId);
}
