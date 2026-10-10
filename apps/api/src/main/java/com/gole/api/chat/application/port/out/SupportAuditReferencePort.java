package com.gole.api.chat.application.port.out;

/** Outbound port: 문의 대화를 가리키는 관리자 감사 기록의 가명화. 파기 트랜잭션 안에서 부른다. */
public interface SupportAuditReferencePort {

    /** 이 문의방을 가리키는 감사 기록의 대상 ID를 파기 영수증 ID로 바꾸고 바꾼 건수를 돌려준다. */
    long pseudonymizeSupportTicketReferences(String roomId, String receiptId);
}
