package com.gole.api.account.application.port.in;

import java.util.Optional;

/**
 * Inbound port: 계정의 현재 자격(관리자·이메일 인증·정지 여부). 다른 컨텍스트가 "이 계정과 대화·거래해도 되는가"를 판단할 때
 * 계정 저장소 대신 이 포트를 본다.
 */
public interface GetAccountStandingUseCase {

    /** 계정이 없으면 비어 있다. */
    Optional<AccountStanding> standing(String accountId);

    record AccountStanding(String accountId, boolean admin, boolean verified, boolean suspended) {}
}
