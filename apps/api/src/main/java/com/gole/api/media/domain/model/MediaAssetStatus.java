package com.gole.api.media.domain.model;

/** 사용자 업로드 미디어의 공개 수명주기 상태. */
public enum MediaAssetStatus {
    /** 업로드한 계정만 미리 볼 수 있고 아직 콘텐츠에는 연결되지 않은 상태. */
    STAGED,
    /** 하나의 draft 콘텐츠에 연결됐지만 소유자만 볼 수 있는 상태. */
    PRIVATE,
    /** 정확히 하나의 공개 콘텐츠에 연결된 상태. */
    PUBLIC,
    /** 공개가 즉시 차단됐고 객체 스토리지 삭제를 기다리는 상태. */
    REVOKED,
    /** 소유권을 안전하게 증명할 수 없는 레거시 데이터 격리 상태. */
    QUARANTINED
}
