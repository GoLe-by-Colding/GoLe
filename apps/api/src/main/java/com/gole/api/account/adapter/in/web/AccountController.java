package com.gole.api.account.adapter.in.web;

import com.gole.api.account.adapter.in.web.AccountRequests.RegisterRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.ResendVerificationRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.SignInRequest;
import com.gole.api.account.adapter.in.web.AccountRequests.VerifyEmailRequest;
import com.gole.api.account.adapter.in.web.AccountResponses.MeResponse;
import com.gole.api.account.adapter.in.web.AccountResponses.RegisterResponse;
import com.gole.api.account.adapter.in.web.AccountResponses.SignInResponse;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.application.port.in.LogoutUseCase;
import com.gole.api.account.application.port.in.RegisterAccountUseCase;
import com.gole.api.account.application.port.in.RegisterAccountUseCase.RegisterAccountCommand;
import com.gole.api.account.application.port.in.ResendVerificationUseCase;
import com.gole.api.account.application.port.in.ResendVerificationUseCase.ResendVerificationCommand;
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInResult;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST). use case 인터페이스에만 의존한다.
 */
@Tag(name = "Account", description = "회원가입·인증·로그인·로그아웃·내정보")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final RegisterAccountUseCase registerAccountUseCase;
    private final ResendVerificationUseCase resendVerificationUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final SignInUseCase signInUseCase;
    private final GetCurrentSessionUseCase getCurrentSessionUseCase;
    private final LogoutUseCase logoutUseCase;

    public AccountController(
            RegisterAccountUseCase registerAccountUseCase,
            ResendVerificationUseCase resendVerificationUseCase,
            VerifyEmailUseCase verifyEmailUseCase,
            SignInUseCase signInUseCase,
            GetCurrentSessionUseCase getCurrentSessionUseCase,
            LogoutUseCase logoutUseCase) {
        this.registerAccountUseCase = registerAccountUseCase;
        this.resendVerificationUseCase = resendVerificationUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.signInUseCase = signInUseCase;
        this.getCurrentSessionUseCase = getCurrentSessionUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @Operation(summary = "회원가입", description = "이메일·비밀번호로 계정을 생성합니다. 이메일 인증 코드가 발송됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "가입 성공 — accountId 반환"),
        @ApiResponse(responseCode = "409", description = "이메일 중복")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        String accountId =
                registerAccountUseCase.register(new RegisterAccountCommand(request.email(), request.password()));
        return new RegisterResponse(accountId);
    }

    @Operation(summary = "이메일 인증", description = "가입 시 발송된 인증 코드로 계정을 활성화합니다.")
    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.verify(new VerifyEmailCommand(request.email(), request.code()));
    }

    @Operation(summary = "이메일 인증 코드 재발급", description = "인증 대기 계정에 새 인증 코드를 발송합니다. 60초 재요청 제한이 적용됩니다.")
    @PostMapping("/verification/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationUseCase.resend(new ResendVerificationCommand(request.email()));
    }

    @Operation(summary = "로그인", description = "이메일·비밀번호로 인증하고 Bearer 세션 토큰을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공 — sessionToken 반환"),
        @ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치"),
        @ApiResponse(responseCode = "423", description = "로그인 잠금(5회 실패)")
    })
    @PostMapping("/sessions")
    public SignInResponse signIn(@Valid @RequestBody SignInRequest request) {
        SignInResult result = signInUseCase.signIn(new SignInCommand(request.email(), request.password()));
        return new SignInResponse(
                result.accountId(), result.sessionToken(), result.role().name());
    }

    @Operation(summary = "내 정보 조회", description = "현재 세션의 계정 ID·이메일·권한을 반환합니다. Authorization: Bearer {token} 필요.")
    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearer(authorization);
        CurrentSession session = getCurrentSessionUseCase
                .resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다"));
        return new MeResponse(
                session.accountId(), session.email(), session.role().name());
    }

    @Operation(summary = "로그아웃", description = "서버 세션을 폐기합니다. Authorization: Bearer {token} 필요.")
    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        logoutUseCase.logout(extractBearer(authorization));
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
