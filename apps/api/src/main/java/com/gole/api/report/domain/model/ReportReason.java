package com.gole.api.report.domain.model;

/**
 * 신고 사유. 가품(위조 레고)·이미지/저작권 도용 신고는 OSP 면책(notice & takedown)의
 * 입구이므로 별도 사유로 분리해 운영자가 우선 처리할 수 있게 한다.
 */
public enum ReportReason {
    /** 가품·위조품 의심 (레핀 등 비정품 브릭) */
    COUNTERFEIT,
    /** 이미지·저작권·상표 도용 (공식 렌더 무단 사용 등) */
    IP_INFRINGEMENT,
    /** 사기·허위 매물 */
    FRAUD,
    /** 욕설·스팸 등 부적절한 콘텐츠 */
    INAPPROPRIATE,
    /** 기타 */
    OTHER
}
