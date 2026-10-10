package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.GetPublicProfilesUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.common.exception.BadRequestException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 공개 표시 이름 조회. 계정 전체가 아니라 닉네임만 투영해서 읽는다. (public-display-name D1) */
@Service
public class PublicProfileService implements GetPublicProfilesUseCase {

    private final AccountRepositoryPort accounts;

    public PublicProfileService(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<PublicProfile> publicProfiles(Collection<String> accountIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (String raw : accountIds) {
            if (raw != null && !raw.isBlank()) {
                ids.add(raw.trim());
            }
        }
        if (ids.size() > MAX_ACCOUNT_IDS) {
            throw new BadRequestException("TOO_MANY_ACCOUNT_IDS", "한 번에 " + MAX_ACCOUNT_IDS + "명까지 조회할 수 있습니다");
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, Nickname> nicknames = accounts.findNicknamesByIds(ids);
        return ids.stream()
                .map(id -> {
                    Nickname nickname = nicknames.get(id);
                    return new PublicProfile(id, nickname == null ? null : nickname.value());
                })
                .toList();
    }
}
