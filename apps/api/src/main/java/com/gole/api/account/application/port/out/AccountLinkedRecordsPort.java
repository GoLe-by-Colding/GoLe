package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.AccountDeletionBlocker;
import java.util.List;
import java.util.Map;

/**
 * Outbound port: 다른 컨텍스트에 남은 이 계정의 기록. 회원 탈퇴가 차단 사유를 묻고, 파기할 때 각 컨텍스트에 처리를 맡긴다.
 * (account-deletion-participants D2)
 *
 * <p>account 는 다른 컨텍스트의 컬렉션·필드·상태를 모른다. 무엇을 지우고 무엇을 익명화할지는 각 컨텍스트가 정한다.
 */
public interface AccountLinkedRecordsPort {

    /** 아직 파기하면 안 되는 사유. 순서는 운영 화면에 보이는 순서다. 명시적 보존 보류는 여기 들어가지 않는다. */
    List<AccountDeletionBlocker> blockers(String accountId);

    /**
     * 각 컨텍스트의 기록을 지우거나 {@code anonymousSubject}로 바꾼다. 감사 기록은 대상 ID 를 {@code receiptId}로 바꾼다.
     *
     * @return 탈퇴 영수증에 남길 처리 건수. 키와 순서는 영수증 형식이다
     */
    Map<String, Long> erase(String accountId, String anonymousSubject, String receiptId);
}
