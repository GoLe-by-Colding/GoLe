package com.gole.api.media.application.port.out;

import java.util.Optional;

/**
 * 객체 스토리지 아웃바운드 포트. S3/MinIO/파일시스템 등 구현 무관. (설계: 헥사고날 경계)
 */
public interface ObjectStoragePort {

    /** 대상 버킷이 없으면 생성한다. (요구사항 M1.5) */
    void ensureBucket();

    /** 객체를 저장한다. */
    void put(String key, byte[] content, String contentType);

    /** 객체를 조회한다. 없으면 빈 Optional. */
    Optional<StoredObject> get(String key);

    /** 객체 하나를 멱등 삭제한다. 이미 없으면 성공으로 본다. */
    void delete(String key);

    /** 신뢰된 내부 프리픽스 아래 객체를 모두 멱등 삭제한다. */
    void deletePrefix(String prefix);

    /**
     * @param content     객체 바이트
     * @param contentType 저장 시 기록된 MIME 타입
     */
    record StoredObject(byte[] content, String contentType) {}
}
