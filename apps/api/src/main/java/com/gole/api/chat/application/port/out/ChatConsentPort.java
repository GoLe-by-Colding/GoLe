package com.gole.api.chat.application.port.out;

/** Outbound port: 제3자 제공 동의 판정. 동의가 없으면 계정 컨텍스트의 거부 예외를 그대로 던진다. */
public interface ChatConsentPort {

    /** 내 정보를 새 대화 상대에게 내보이기 전에 내 현재 동의를 확인한다. */
    void requireCurrent(String accountId);

    /** 다른 이용자(정보주체)를 새 대화에 내보이기 전에 그 사람의 현재 동의를 확인한다. */
    void requireCurrentSubject(String accountId);
}
