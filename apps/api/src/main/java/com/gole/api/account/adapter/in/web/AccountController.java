package com.gole.api.account.adapter.in.web;

import com.gole.api.account.adapter.in.web.AccountRequests.RegisterRequest;
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
import com.gole.api.account.application.port.in.SignInUseCase;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInResult;
import com.gole.api.account.application.port.in.VerifyEmailUseCase;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST). use case 인터페이스에만 의존한다.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final RegisterAccountUseCase registerAccountUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final SignInUseCase signInUseCase;
    private final GetCurrentSessionUseCase getCurrentSessionUseCase;
    private final LogoutUseCase logoutUseCase;

    public AccountController(
            RegisterAccountUseCase registerAccountUseCase,
            VerifyEmailUseCase verifyEmailUseCase,
            SignInUseCase signInUseCase,
            GetCurrentSessionUseCase getCurrentSessionUseCase,
            LogoutUseCase logoutUseCase) {
        this.registerAccountUseCase = registerAccountUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.signInUseCase = signInUseCase;
        this.getCurrentSessionUseCase = getCurrentSessionUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        String accountId = registerAccountUseCase.register(
                new RegisterAccountCommand(request.email(), request.password()));
        return new RegisterResponse(accountId);
    }

    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.verify(new VerifyEmailCommand(request.email(), request.code()));
    }

    @PostMapping("/sessions")
    public SignInResponse signIn(@Valid @RequestBody SignInRequest request) {
        SignInResult result =
                signInUseCase.signIn(new SignInCommand(request.email(), request.password()));
        return new SignInResponse(result.accountId(), result.sessionToken(), result.role().name());
    }

    /** 현재 세션 해석. Authorization: Bearer <token> 필요. 프론트의 역할(권한) 확인에 사용. */
    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearer(authorization);
        CurrentSession session = getCurrentSessionUseCase.resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다"));
        return new MeResponse(session.accountId(), session.role().name());
    }

    /** 로그아웃: 서버측 세션을 폐기한다. Authorization: Bearer <token>. */
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
