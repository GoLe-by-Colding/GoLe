package com.gole.api.design.application.port.out;

import java.util.List;

/** 마스코트 이미지의 공개 경로와 공개 연결·회수. 미디어 컨텍스트에 맡긴다. */
public interface MascotMediaPort {

    /** 신뢰할 수 있는 저장 키를 같은 원점 공개 경로로 바꾼다. 키가 아니면 거부한다. */
    String publicPath(String mediaKey);

    /** 올린 관리자 본인의 staged 키를 에셋에 공개 연결한다. */
    void attach(String ownerId, String assetId, List<String> mediaKeys);

    /** 에셋에 연결된 이미지를 회수한다. */
    void release(String assetId);
}
