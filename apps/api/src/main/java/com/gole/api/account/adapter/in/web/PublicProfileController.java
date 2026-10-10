package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetPublicProfilesUseCase;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 사람의 공개 표시 이름. 커뮤니티·매물·셀러 샵이 비로그인 공개 화면이라 로그인 없이 읽는다.
 * (public-display-name D1, R3)
 *
 * <p>{@code ids}는 쉼표로 잇거나({@code ?ids=a,b}) 반복해서({@code ?ids=a&ids=b}) 보낸다.
 */
@RestController
@RequestMapping("/api/v1/accounts/public-profiles")
public class PublicProfileController {

    /** 닉네임은 바뀌어도 잠깐 늦게 보여도 되는 정보다. 같은 화면을 오가는 동안의 재조회만 줄인다. */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(1));

    private final GetPublicProfilesUseCase publicProfiles;

    public PublicProfileController(GetPublicProfilesUseCase publicProfiles) {
        this.publicProfiles = publicProfiles;
    }

    @GetMapping
    public ResponseEntity<List<PublicProfileResponse>> publicProfiles(
            @RequestParam(defaultValue = "") List<String> ids) {
        List<PublicProfileResponse> body = publicProfiles.publicProfiles(ids).stream()
                .map(profile -> new PublicProfileResponse(profile.accountId(), profile.nickname()))
                .toList();
        return ResponseEntity.ok().cacheControl(CACHE).body(body);
    }

    public record PublicProfileResponse(String accountId, String nickname) {}
}
