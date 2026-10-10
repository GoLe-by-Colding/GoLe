package com.gole.api.chat.application.port.in;

/** Inbound port: 채팅 메시지 신고. 신고 시점의 앞뒤 대화를 증거 사본으로 함께 고정한다. */
public interface ReportChatMessageUseCase {

    /**
     * @param reasonCode 신고 사유 코드(웹 경계에서 신고 컨텍스트의 사유로 검증된 값)
     * @return 접수된 신고 id
     */
    String report(String reporterId, String messageId, String reasonCode, String detail);
}
