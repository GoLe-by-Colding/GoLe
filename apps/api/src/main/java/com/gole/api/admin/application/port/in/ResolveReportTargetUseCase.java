package com.gole.api.admin.application.port.in;

/**
 * Inbound port: 신고 대상 조치 후 신고 완료(매물 내림·게시글 삭제·댓글/후기 블라인드). 대상 조치와 신고 완료는 한 트랜잭션이다. 채팅 신고는
 * 스냅샷 검토 뒤 별도 계정 조치를 쓰므로 여기서 받지 않는다. 이미 처리된 신고는 대상에 손대지 않고 거부한다.
 */
public interface ResolveReportTargetUseCase {

    void resolve(String reportId, String reason);
}
