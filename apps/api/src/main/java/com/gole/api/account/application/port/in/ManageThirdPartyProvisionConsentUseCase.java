package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import java.time.Instant;

/**
 * Inbound port: 제3자 제공 동의의 현재 상태 판정과 동의·철회 기록.
 *
 * <p>새 개인정보 제공을 수반하는 기능(채팅방 개설·제안·주문)은 {@link #requireCurrent}·{@link #requireCurrentSubject} 로
 * 서버측 최종 판정을 받는다. 과거 버전의 동의나 철회 전 동의는 새 제공의 근거가 되지 않는다.
 */
public interface ManageThirdPartyProvisionConsentUseCase {

    String REQUIRED_CODE = "THIRD_PARTY_PROVISION_CONSENT_REQUIRED";
    String SUBJECT_REQUIRED_CODE = "THIRD_PARTY_PROVISION_SUBJECT_CONSENT_REQUIRED";

    ConsentStatus currentStatus(String accountId);

    /** 새 개인정보 제공을 수반하는 기능의 서버측 최종 gate. 동의가 없으면 {@link #REQUIRED_CODE} 로 거부한다. */
    void requireCurrent(String accountId);

    /** 다른 이용자의 개인정보를 새 수신자에게 제공할 때 정보주체의 현재 동의를 확인한다({@link #SUBJECT_REQUIRED_CODE}). */
    void requireCurrentSubject(String accountId);

    ConsentStatus consent(String accountId, String noticeVersion, SourcePath path, String requestId);

    ConsentStatus withdraw(String accountId, String noticeVersion, String requestId);

    record ConsentStatus(String noticeVersion, boolean consented, Instant lastDecisionAt) {}
}
