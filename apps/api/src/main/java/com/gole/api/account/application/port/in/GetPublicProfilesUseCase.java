package com.gole.api.account.application.port.in;

import java.util.Collection;
import java.util.List;

/**
 * Inbound port: 다른 사람에게 보여 줄 공개 표시 이름. (public-display-name D1)
 *
 * <p>닉네임만 낸다. 이메일·전화번호·상태·권한은 공개 화면에 필요 없고, 닉네임은 본인이 남에게 보이라고
 * 정한 유일한 표시 이름이라 로그인 없이 읽어도 된다.
 */
public interface GetPublicProfilesUseCase {

    /** 한 번에 물을 수 있는 계정 수. 화면 하나가 보여 주는 사람 수보다 넉넉하다. */
    int MAX_ACCOUNT_IDS = 50;

    /**
     * 요청 순서대로 계정마다 한 줄을 돌려준다. 빈 값·중복 ID는 버린다.
     *
     * <p>닉네임이 없거나 계정이 없으면(탈퇴로 파기됨) {@code nickname}이 {@code null}이다 — 화면이 "없음"도
     * 캐시해 같은 ID를 다시 묻지 않게 하려는 것이다.
     *
     * @throws com.gole.api.common.exception.BadRequestException ID가 {@link #MAX_ACCOUNT_IDS}개를 넘을 때
     */
    List<PublicProfile> publicProfiles(Collection<String> accountIds);

    record PublicProfile(String accountId, String nickname) {}
}
