package com.gole.api.account.adapter.in.web;

import com.gole.api.account.adapter.in.web.OnboardingRequests.ConfirmPhoneVerificationRequest;
import com.gole.api.account.adapter.in.web.OnboardingRequests.RequestPhoneVerificationRequest;
import com.gole.api.account.adapter.in.web.OnboardingRequests.SelectInterestTagsRequest;
import com.gole.api.account.adapter.in.web.OnboardingRequests.SetNicknameRequest;
import com.gole.api.account.adapter.in.web.OnboardingRequests.SubmitConsentRequest;
import com.gole.api.account.adapter.in.web.OnboardingResponses.InterestTagsResponse;
import com.gole.api.account.adapter.in.web.OnboardingResponses.OnboardingStatusResponse;
import com.gole.api.account.adapter.in.web.OnboardingResponses.PhoneVerificationResponse;
import com.gole.api.account.application.port.in.ConfirmPhoneVerificationUseCase;
import com.gole.api.account.application.port.in.ConfirmPhoneVerificationUseCase.ConfirmPhoneVerificationCommand;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase;
import com.gole.api.account.application.port.in.ListInterestTagsUseCase;
import com.gole.api.account.application.port.in.RequestPhoneVerificationUseCase;
import com.gole.api.account.application.port.in.RequestPhoneVerificationUseCase.RequestPhoneVerificationCommand;
import com.gole.api.account.application.port.in.SelectInterestTagsUseCase;
import com.gole.api.account.application.port.in.SelectInterestTagsUseCase.SelectInterestTagsCommand;
import com.gole.api.account.application.port.in.SetNicknameUseCase;
import com.gole.api.account.application.port.in.SetNicknameUseCase.SetNicknameCommand;
import com.gole.api.account.application.port.in.SubmitOnboardingConsentUseCase;
import com.gole.api.account.application.port.in.SubmitOnboardingConsentUseCase.SubmitConsentCommand;
import com.gole.api.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 최초 로그인 온보딩. (onboarding R2~R7)
 *
 * <p>{@code AccountController}와 분리한 이유 — 가입·로그인 흐름과 수명주기가 다르고(한 계정에
 * 한 번), 단계가 6개라 한 컨트롤러에 합치면 두 흐름 어느 쪽도 읽기 어려워진다.
 *
 * <p>세션을 직접 해석하는 이유 — {@code UserWebConfig}가 {@code /api/v1/accounts/**}를 가드에서
 * 제외하고 있어(가입·로그인이 그 아래 있다) 인터셉터가 계정 속성을 채워 주지 않는다.
 * {@code AccountController.me()}와 같은 방식이다.
 */
@Tag(name = "Onboarding", description = "최초 로그인 온보딩 — 닉네임·전화인증·관심태그·약관동의")
@RestController
public class OnboardingController {

    private final GetOnboardingStatusUseCase getOnboardingStatusUseCase;
    private final ListInterestTagsUseCase listInterestTagsUseCase;
    private final SetNicknameUseCase setNicknameUseCase;
    private final RequestPhoneVerificationUseCase requestPhoneVerificationUseCase;
    private final ConfirmPhoneVerificationUseCase confirmPhoneVerificationUseCase;
    private final SelectInterestTagsUseCase selectInterestTagsUseCase;
    private final SubmitOnboardingConsentUseCase submitOnboardingConsentUseCase;
    private final GetCurrentSessionUseCase getCurrentSessionUseCase;
    private final SessionCookie sessionCookie;

    public OnboardingController(
            GetOnboardingStatusUseCase getOnboardingStatusUseCase,
            ListInterestTagsUseCase listInterestTagsUseCase,
            SetNicknameUseCase setNicknameUseCase,
            RequestPhoneVerificationUseCase requestPhoneVerificationUseCase,
            ConfirmPhoneVerificationUseCase confirmPhoneVerificationUseCase,
            SelectInterestTagsUseCase selectInterestTagsUseCase,
            SubmitOnboardingConsentUseCase submitOnboardingConsentUseCase,
            GetCurrentSessionUseCase getCurrentSessionUseCase,
            SessionCookie sessionCookie) {
        this.getOnboardingStatusUseCase = getOnboardingStatusUseCase;
        this.listInterestTagsUseCase = listInterestTagsUseCase;
        this.setNicknameUseCase = setNicknameUseCase;
        this.requestPhoneVerificationUseCase = requestPhoneVerificationUseCase;
        this.confirmPhoneVerificationUseCase = confirmPhoneVerificationUseCase;
        this.selectInterestTagsUseCase = selectInterestTagsUseCase;
        this.submitOnboardingConsentUseCase = submitOnboardingConsentUseCase;
        this.getCurrentSessionUseCase = getCurrentSessionUseCase;
        this.sessionCookie = sessionCookie;
    }

    @Operation(summary = "온보딩 진행 상태", description = "단계별 완료 여부와 required·legacyExempt를 반환합니다. 이탈 후 재개에 사용합니다.")
    @GetMapping("/api/v1/accounts/me/onboarding")
    public OnboardingStatusResponse status(HttpServletRequest http) {
        return OnboardingStatusResponse.from(getOnboardingStatusUseCase.status(currentAccountId(http)));
    }

    @Operation(summary = "선택 가능한 관심 태그", description = "온보딩에서 고를 수 있는 curated 레고 테마 목록입니다. 로그인 없이 조회할 수 있습니다.")
    @GetMapping("/api/v1/account/interest-tags")
    public InterestTagsResponse interestTags() {
        return InterestTagsResponse.from(listInterestTagsUseCase.availableTags());
    }

    @Operation(summary = "닉네임 설정", description = "2~12자 한글·영문·숫자. 대소문자를 무시하고 유일해야 합니다.")
    @PutMapping("/api/v1/accounts/me/onboarding/nickname")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setNickname(@Valid @RequestBody SetNicknameRequest request, HttpServletRequest http) {
        setNicknameUseCase.setNickname(new SetNicknameCommand(currentAccountId(http), request.nickname()));
    }

    @Operation(summary = "전화번호 인증 코드 발송", description = "휴대폰 번호로 6자리 코드를 발송합니다. 60초 재요청 제한과 일일 발송 한도가 적용됩니다.")
    @PostMapping("/api/v1/accounts/me/onboarding/phone/verification")
    public PhoneVerificationResponse requestPhoneVerification(
            @Valid @RequestBody RequestPhoneVerificationRequest request, HttpServletRequest http) {
        return PhoneVerificationResponse.from(requestPhoneVerificationUseCase.request(
                new RequestPhoneVerificationCommand(currentAccountId(http), request.phoneNumber())));
    }

    @Operation(summary = "전화번호 인증 코드 확인", description = "코드가 일치하면 전화번호 인증을 완료합니다. 5회 오답 시 코드가 무효화됩니다.")
    @PostMapping("/api/v1/accounts/me/onboarding/phone/verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPhoneVerification(
            @Valid @RequestBody ConfirmPhoneVerificationRequest request, HttpServletRequest http) {
        confirmPhoneVerificationUseCase.confirm(
                new ConfirmPhoneVerificationCommand(currentAccountId(http), request.code()));
    }

    @Operation(summary = "관심 태그 선택", description = "제공된 목록 중 1~5개를 선택합니다.")
    @PutMapping("/api/v1/accounts/me/onboarding/interest-tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void selectInterestTags(@Valid @RequestBody SelectInterestTagsRequest request, HttpServletRequest http) {
        selectInterestTagsUseCase.select(new SelectInterestTagsCommand(currentAccountId(http), request.tags()));
    }

    @Operation(summary = "약관 동의", description = "개인정보 수집·이용 동의는 필수, 마케팅 수신 동의는 선택입니다.")
    @PostMapping("/api/v1/accounts/me/onboarding/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitConsent(@Valid @RequestBody SubmitConsentRequest request, HttpServletRequest http) {
        submitOnboardingConsentUseCase.submit(new SubmitConsentCommand(
                currentAccountId(http), request.privacyConsented(), request.marketingConsented()));
    }

    private String currentAccountId(HttpServletRequest http) {
        CurrentSession session = getCurrentSessionUseCase
                .resolve(sessionCookie.resolve(http))
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
        return session.accountId();
    }
}
