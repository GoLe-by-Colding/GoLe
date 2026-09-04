package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService.ConsentStatus;
import com.gole.api.account.domain.model.ThirdPartyProvisionConsentEvent.SourcePath;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 제3자 제공 동의 상태·동의·철회를 다루는 명시적 API. */
@Tag(name = "Account Policy", description = "거래 상대방·대화 참여자 대상 제3자 제공 동의")
@RestController
@RequestMapping("/api/v1/accounts/me")
public class ThirdPartyProvisionConsentController {

    private final ThirdPartyProvisionConsentService consents;
    private final GetCurrentSessionUseCase sessions;
    private final SessionCookie sessionCookie;

    public ThirdPartyProvisionConsentController(
            ThirdPartyProvisionConsentService consents,
            GetCurrentSessionUseCase sessions,
            SessionCookie sessionCookie) {
        this.consents = consents;
        this.sessions = sessions;
        this.sessionCookie = sessionCookie;
    }

    @Operation(summary = "현재 제3자 제공 동의 상태")
    @GetMapping("/third-party-provision-consents/current")
    public ConsentStatusResponse current(HttpServletRequest http) {
        return ConsentStatusResponse.from(consents.currentStatus(currentAccountId(http)));
    }

    @Operation(summary = "제3자 제공 동의", description = "현재 고지 버전에 대한 동의를 append-only 이벤트로 기록합니다.")
    @PostMapping("/third-party-provision-consents")
    public ConsentStatusResponse consent(@Valid @RequestBody ConsentRequest request, HttpServletRequest http) {
        if (!isInteractivePath(request.path())) {
            throw new BadRequestException("CONSENT_PATH_INVALID", "사용자 동의 경로를 확인해 주세요");
        }
        return ConsentStatusResponse.from(
                consents.consent(currentAccountId(http), request.noticeVersion(), request.path(), request.requestId()));
    }

    @Operation(summary = "제3자 제공 동의 철회", description = "과거 대화 열람은 유지하고 새 제공 기능만 중단합니다.")
    @PostMapping("/third-party-provision-consent-withdrawals")
    public ConsentStatusResponse withdraw(@Valid @RequestBody WithdrawalRequest request, HttpServletRequest http) {
        return ConsentStatusResponse.from(
                consents.withdraw(currentAccountId(http), request.noticeVersion(), request.requestId()));
    }

    private String currentAccountId(HttpServletRequest http) {
        CurrentSession session = sessions.resolve(sessionCookie.resolve(http))
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
        return session.accountId();
    }

    private static boolean isInteractivePath(SourcePath path) {
        return path != null
                && switch (path) {
                    case LISTING_CHAT,
                            SOCIAL_DIRECT_CHAT,
                            SOCIAL_GROUP_CHAT,
                            SOCIAL_GROUP_INVITE,
                            CHAT_MESSAGE,
                            ORDER_CONTACTS,
                            ACCOUNT_SETTINGS -> true;
                    case EMAIL_SIGNUP, SOCIAL_GOOGLE_SIGNUP, SOCIAL_KAKAO_SIGNUP, SOCIAL_NAVER_SIGNUP -> false;
                };
    }

    public record ConsentRequest(
            @NotBlank @Size(max = 64) String noticeVersion,
            @AssertTrue(message = "제3자 제공에 동의해야 합니다") boolean accepted,
            SourcePath path,
            @NotBlank @Size(max = 160) String requestId) {}

    public record WithdrawalRequest(
            @NotBlank @Size(max = 64) String noticeVersion, @NotBlank @Size(max = 160) String requestId) {}

    public record ConsentStatusResponse(String noticeVersion, boolean consented, Instant lastDecisionAt) {

        static ConsentStatusResponse from(ConsentStatus status) {
            return new ConsentStatusResponse(status.noticeVersion(), status.consented(), status.lastDecisionAt());
        }
    }
}
