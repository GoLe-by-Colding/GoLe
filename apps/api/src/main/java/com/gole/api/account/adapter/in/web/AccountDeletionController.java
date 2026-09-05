package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.concurrency.AccountMutationGate;
import com.gole.api.account.application.concurrency.AccountMutationGate.Lease;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase.Command;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase.Result;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 본인 이메일 재인증을 거치는 회원 탈퇴 요청 API. */
@Tag(name = "Account", description = "회원가입·인증·로그인·로그아웃·내정보")
@RestController
@RequestMapping("/api/v1/accounts/me")
public class AccountDeletionController {

    private final RequestAccountDeletionUseCase accountDeletion;
    private final GetCurrentSessionUseCase currentSession;
    private final SessionCookie sessionCookie;
    private final AccountMutationGate mutationGate;

    public AccountDeletionController(
            RequestAccountDeletionUseCase accountDeletion,
            GetCurrentSessionUseCase currentSession,
            SessionCookie sessionCookie,
            AccountMutationGate mutationGate) {
        this.accountDeletion = accountDeletion;
        this.currentSession = currentSession;
        this.sessionCookie = sessionCookie;
        this.mutationGate = mutationGate;
    }

    @Operation(summary = "회원 탈퇴 본인확인 코드 발송", description = "이메일 발송이 준비된 때 현재 로그인 계정 이메일로 10분짜리 일회용 코드를 발송합니다.")
    @PostMapping("/deletion-verifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void issueVerification(HttpServletRequest request) {
        accountDeletion.issueVerification(requireAccountId(request));
    }

    @Operation(summary = "회원 탈퇴 요청", description = "이메일 발송이 준비된 때 이메일·확인 문구·일회용 코드를 검증한 뒤 계정을 즉시 비활성화하고 모든 세션을 폐기합니다.")
    @PostMapping("/deletion-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeletionResponse request(
            @Valid @RequestBody DeletionRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {
        String token = sessionCookie.resolve(request);
        String expectedAccountId = requireAccountId(token);
        Result result;
        try (Lease ignored = mutationGate.acquireExclusive(expectedAccountId)) {
            String revalidatedAccountId = requireAccountId(token);
            if (!expectedAccountId.equals(revalidatedAccountId)) {
                throw new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다");
            }
            result = accountDeletion.request(new Command(
                    revalidatedAccountId, body.email(), body.confirmation(), body.verificationCode(), idempotencyKey));
        }
        sessionCookie.clear(request, response);
        return DeletionResponse.from(result);
    }

    private String requireAccountId(HttpServletRequest request) {
        return requireAccountId(sessionCookie.resolve(request));
    }

    private String requireAccountId(String token) {
        return currentSession
                .resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다"))
                .accountId();
    }

    public record DeletionRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 20) String confirmation,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String verificationCode) {}

    public record DeletionResponse(
            String requestId,
            AccountDeletionStatus status,
            List<AccountDeletionBlocker> blockers,
            Instant requestedAt) {
        static DeletionResponse from(Result result) {
            return new DeletionResponse(result.requestId(), result.status(), result.blockers(), result.requestedAt());
        }
    }
}
