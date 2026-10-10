package com.gole.api.promotion.application.port.out;

import java.util.List;

/** Outbound port: 홍보 초안 미디어(공개 경로 변환·초안 연결). */
public interface PromotionMediaPort {

    /** 스테이지 키를 브라우저가 여는 공개 경로로 바꾼다. 신뢰할 수 없는 키면 {@link IllegalArgumentException}. */
    String publicPath(String mediaKey);

    /** 검증한 초안에 게시 이미지와 다듬기 전 원본을 공개로 연결한다. */
    void attachToPost(String authorId, String postId, List<String> mediaKeys);
}
