package com.gole.api.chat.application.port.out;

/** Outbound port: 채팅 메시지 신고 접수. */
public interface ChatMessageReportPort {

    /**
     * @param reasonCode 신고 컨텍스트의 사유 코드(웹 경계에서 검증된 값)
     * @return 접수된 신고 id
     */
    String submit(String reporterId, String messageId, String reasonCode, String detail);
}
