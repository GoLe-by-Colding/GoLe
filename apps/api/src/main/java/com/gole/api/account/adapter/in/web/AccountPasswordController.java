package com.gole.api.account.adapter.in.web;

import com.gole.api.account.adapter.in.web.AccountRequests.ChangePasswordRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.ConfirmPasswordResetRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.RequestPasswordResetRequest;
import com.gole.api.account.application.port.in.ChangePasswordUseCase;
import com.gole.api.account.application.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase.ConfirmPasswordResetCommand;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.PublicAuthRequestLimitUseCase;
import com.gole.api.account.application.port.in.RequestPasswordResetUseCase;
import com.gole.api.account.application.port.in.RequestPasswordResetUseCase.RequestPasswordResetCommand;
import com.gole.api.account.config.EmailAuthenticationAvailability;
import com.gole.api.common.exception.UnauthorizedException;
import com.gole.api.common.web.ClientAddressResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 변경·복구 전용 inbound 어댑터. 계정 존재 여부를 응답으로 노출하지 않는다. */
@Tag(name = "Account", description = "회원가입·인증·로그인·로그아웃·내정보")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountPasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    private final GetCurrentSessionUseCase getCurrentSessionUseCase;
    private final SessionCookie sessionCookie;
    private final PublicAuthRequestLimitUseCase publicRequestLimit;
    private final ClientAddressResolver clientAddresses;
    private final EmailAuthenticationAvailability emailAuthentication;

    public AccountPasswordController(
            ChangePasswordUseCase changePasswordUseCase,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ConfirmPasswordResetUseCase confirmPasswordResetUseCase,
            GetCurrentSessionUseCase getCurrentSessionUseCase,
            SessionCookie sessionCookie,
            PublicAuthRequestLimitUseCase publicRequestLimit,
            ClientAddressResolver clientAddresses,
            EmailAuthenticationAvailability emailAuthentication) {
        this.changePasswordUseCase = changePasswordUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.confirmPasswordResetUseCase = confirmPasswordResetUseCase;
        this.getCurrentSessionUseCase = getCurrentSessionUseCase;
        this.sessionCookie = sessionCookie;
        this.publicRequestLimit = publicRequestLimit;
        this.clientAddresses = clientAddresses;
        this.emailAuthentication = emailAuthentication;
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인하고 변경한 뒤 모든 기기의 세션을 폐기합니다.")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request, HttpServletResponse response) {
        String token = sessionCookie.resolve(request);
        String accountId = getCurrentSessionUseCase
                .resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다"))
                .accountId();
        changePasswordUseCase.change(new ChangePasswordCommand(accountId, body.currentPassword(), body.newPassword()));
        sessionCookie.clear(request, response);
    }

    @Operation(summary = "비밀번호 재설정 코드 요청", description = "이메일 발송이 준비된 때 가입 여부와 무관하게 같은 응답을 반환합니다.")
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestReset(@Valid @RequestBody RequestPasswordResetRequest body, HttpServletRequest request) {
        emailAuthentication.requireAvailable();
        if (!publicRequestLimit.acquirePasswordReset(body.email(), clientAddresses.resolve(request))) {
            return;
        }
        requestPasswordResetUseCase.request(new RequestPasswordResetCommand(body.email()));
    }

    @Operation(summary = "비밀번호 재설정 확정", description = "이메일 발송이 준비된 때 10분짜리 일회용 코드로 비밀번호를 바꾸고 모든 세션을 폐기합니다.")
    @PostMapping("/password-reset/confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody ConfirmPasswordResetRequest body) {
        emailAuthentication.requireAvailable();
        confirmPasswordResetUseCase.confirm(
                new ConfirmPasswordResetCommand(body.email(), body.code(), body.newPassword()));
    }
}
