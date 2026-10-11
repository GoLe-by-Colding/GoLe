package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetPublicProfilesUseCase.PublicProfile;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicProfileServiceTest {

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final PublicProfileService service = new PublicProfileService(accounts);

    @Test
    @DisplayName("요청 순서대로 한 줄씩 돌려주고, 닉네임이 없거나 계정이 없으면 null 로 채운다")
    void publicProfiles_keepsRequestOrderAndFillsMissingWithNull() {
        when(accounts.findNicknamesByIds(Set.of("b", "a", "gone")))
                .thenReturn(Map.of("a", new Nickname("브릭고래"), "b", new Nickname("gole01")));

        List<PublicProfile> profiles = service.publicProfiles(List.of("b", "a", "gone"));

        assertThat(profiles)
                .containsExactly(
                        new PublicProfile("b", "gole01"),
                        new PublicProfile("a", "브릭고래"),
                        new PublicProfile("gone", null));
    }

    @Test
    @DisplayName("빈 값과 중복 ID 는 버리고 앞뒤 공백을 지운다")
    void publicProfiles_dropsBlankAndDuplicateIds() {
        when(accounts.findNicknamesByIds(Set.of("a", "b"))).thenReturn(Map.of());

        List<PublicProfile> profiles = service.publicProfiles(java.util.Arrays.asList(" a ", "", null, "b", "a"));

        assertThat(profiles).extracting(PublicProfile::accountId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("묻는 ID 가 없으면 저장소를 읽지 않는다")
    void publicProfiles_skipsRepositoryForEmptyRequest() {
        assertThat(service.publicProfiles(List.of(" ", ""))).isEmpty();

        verify(accounts, never()).findNicknamesByIds(any());
    }

    @Test
    @DisplayName("50명을 넘게 물으면 TOO_MANY_ACCOUNT_IDS 로 거부한다")
    void publicProfiles_rejectsMoreThanFiftyIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ids.add("account-" + i);
        }

        assertThatThrownBy(() -> service.publicProfiles(ids))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_ACCOUNT_IDS");
        verify(accounts, never()).findNicknamesByIds(any());
    }
}
