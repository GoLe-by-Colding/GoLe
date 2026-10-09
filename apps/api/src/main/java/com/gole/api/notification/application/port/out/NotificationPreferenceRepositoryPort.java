package com.gole.api.notification.application.port.out;

import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Outbound port: 알림 수신 설정 영속성. 계정당 문서 하나다. */
public interface NotificationPreferenceRepositoryPort {

    /** 저장한 적이 없으면 비어 있다. 기본값을 채우는 것은 호출자 몫이다. */
    Optional<NotificationPreferences> find(String accountId);

    /** 계정 id를 키로 덮어쓴다. */
    NotificationPreferences save(NotificationPreferences preferences);

    /**
     * 주어진 계정 가운데 이 분류를 꺼둔 계정만 돌려준다.
     *
     * <p>알림톡 팬아웃은 한 번에 수백 명을 훑는다. 계정마다 {@link #find}를 부르지 않도록 한 번에 묻는다.
     */
    Set<String> findAccountsDisabling(NotificationCategory category, Collection<String> accountIds);
}
