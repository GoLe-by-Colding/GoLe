package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.ManageAccountDeletionRequestsUseCase;
import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase;
import com.gole.api.account.application.port.out.AccountDeletionRepositoryPort;
import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort;
import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort.Challenge;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort;
import com.gole.api.account.application.port.out.PhoneVerificationStorePort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionHoldReason;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴 본인확인, 즉시 접근 차단, 보존 검토 및 관리자 파기를 조율한다. */
@Service
public class AccountDeletionService implements RequestAccountDeletionUseCase, ManageAccountDeletionRequestsUseCase {

    public static final String CONFIRMATION_PHRASE = "회원 탈퇴";
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final AccountRepositoryPort accounts;
    private final AccountDeletionRepositoryPort requests;
    private final AccountDeletionVerificationStorePort verifications;
    private final PasswordHasherPort passwordHasher;
    private final VerificationCodeGeneratorPort codeGenerator;
    private final VerificationCodeSenderPort codeSender;
    private final SessionStorePort sessions;
    private final PasswordResetChallengeStorePort passwordResets;
    private final PhoneVerificationStorePort phoneVerifications;
    private final Clock clock;

    public AccountDeletionService(
            AccountRepositoryPort accounts,
            AccountDeletionRepositoryPort requests,
            AccountDeletionVerificationStorePort verifications,
            PasswordHasherPort passwordHasher,
            VerificationCodeGeneratorPort codeGenerator,
            VerificationCodeSenderPort codeSender,
            SessionStorePort sessions,
            PasswordResetChallengeStorePort passwordResets,
            PhoneVerificationStorePort phoneVerifications,
            Clock clock) {
        this.accounts = accounts;
        this.requests = requests;
        this.verifications = verifications;
        this.passwordHasher = passwordHasher;
        this.codeGenerator = codeGenerator;
        this.codeSender = codeSender;
        this.sessions = sessions;
        this.passwordResets = passwordResets;
        this.phoneVerifications = phoneVerifications;
        this.clock = clock;
    }

    @Override
    public void issueVerification(String accountId) {
        Account account = activeUser(accountId);
        Instant now = Instant.now(clock);
        var existing = verifications.find(accountId);
        if (existing.isPresent() && now.isBefore(existing.get().issuedAt().plus(RESEND_COOLDOWN))) {
            return;
        }
        String rawCode = codeGenerator.generateCode();
        PasswordHash hash = passwordHasher.hash(rawCode);
        verifications.store(accountId, new Challenge(hash.value(), now, 0), VERIFICATION_TTL);
        codeSender.sendAccountDeletion(account.getEmail(), new VerificationCode(rawCode, now));
    }

    @Override
    @Transactional
    public RequestAccountDeletionUseCase.Result request(RequestAccountDeletionUseCase.Command command) {
        UUID idempotencyKey = parseKey(command.idempotencyKey());
        Account account = accounts.findById(command.accountId())
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
        Email confirmedEmail = parseEmail(command.emailConfirmation());
        validateStrongConfirmation(account, confirmedEmail, command.confirmationPhrase());

        String keyHash = sha256(idempotencyKey.toString());
        String fingerprint = keyedFingerprint(
                idempotencyKey,
                "account-deletion-request\n"
                        + account.getId() + "\n"
                        + confirmedEmail.value() + "\n"
                        + command.confirmationPhrase() + "\n"
                        + command.verificationCode());
        var existing = requests.findActiveByAccountId(account.getId());
        if (existing.isPresent()) {
            if (existing.get().requestMatches(keyHash, fingerprint)) {
                return toUserResult(existing.get());
            }
            throw new ConflictException("ACCOUNT_DELETION_ALREADY_REQUESTED", "이미 처리 중인 탈퇴 요청이 있습니다");
        }

        account.ensureNotSuspended();
        if (!account.isVerified()) {
            throw new ForbiddenException("ACCOUNT_NOT_VERIFIED", "이메일 인증을 완료한 계정만 탈퇴를 요청할 수 있습니다");
        }
        if (account.getRole() != Role.USER) {
            throw new ForbiddenException("ADMIN_ACCOUNT_DELETION_FORBIDDEN", "관리자 계정은 일반 탈퇴 경로를 사용할 수 없습니다");
        }
        verifyAndConsume(account.getId(), command.verificationCode());

        Instant now = Instant.now(clock);
        String requestId = UUID.randomUUID().toString();
        List<AccountDeletionBlocker> blockers = requests.evaluateBlockers(account.getId(), false);
        AccountDeletionRequest deletionRequest =
                AccountDeletionRequest.requested(requestId, account.getId(), keyHash, fingerprint, blockers, now);

        account.suspend(AccountDeletionRequest.suspensionReason(requestId));
        accounts.save(account);
        AccountDeletionRequest saved = requests.save(deletionRequest);
        // 실패하면 Mongo 트랜잭션을 롤백한다. 성공했으나 Mongo 커밋이 실패한 경우에도 세션 폐기는 안전하다.
        sessions.revokeAllForAccount(account.getId());
        return toUserResult(saved);
    }

    @Override
    public List<ManageAccountDeletionRequestsUseCase.Result> list(
            AccountDeletionStatus status, int limit, String actorId) {
        requireAdmin(actorId);
        return requests.findRecent(status, limit).stream()
                .map(AccountDeletionService::toAdminResult)
                .toList();
    }

    @Override
    public ManageAccountDeletionRequestsUseCase.Result review(String requestId, String actorId) {
        requireAdmin(actorId);
        AccountDeletionRequest request = activeRequest(requestId);
        request.review(requests.evaluateBlockers(request.getAccountId(), request.isHeld()), Instant.now(clock));
        return toAdminResult(requests.save(request));
    }

    @Override
    public ManageAccountDeletionRequestsUseCase.Result placeHold(
            String requestId, String confirmation, AccountDeletionHoldReason reason, String actorId) {
        requireAdmin(actorId);
        requireExactRequestConfirmation(requestId, confirmation);
        AccountDeletionRequest request = activeRequest(requestId);
        request.placeHold(reason, actorId, Instant.now(clock));
        return toAdminResult(requests.save(request));
    }

    @Override
    public ManageAccountDeletionRequestsUseCase.Result releaseHold(
            String requestId, String confirmation, String actorId) {
        requireAdmin(actorId);
        requireExactRequestConfirmation(requestId, confirmation);
        AccountDeletionRequest request = activeRequest(requestId);
        request.releaseHold(Instant.now(clock));
        request.review(requests.evaluateBlockers(request.getAccountId(), false), Instant.now(clock));
        return toAdminResult(requests.save(request));
    }

    @Override
    public ManageAccountDeletionRequestsUseCase.Result complete(ManageAccountDeletionRequestsUseCase.Command command) {
        requireAdmin(command.actorId());
        requireExactRequestConfirmation(command.requestId(), command.confirmation());
        if (!command.preservationReviewed()) {
            throw new BadRequestException("PRESERVATION_REVIEW_REQUIRED", "진행 거래·분쟁·신고·법정 보존 검토를 명시적으로 확인해야 합니다");
        }
        UUID key = parseKey(command.idempotencyKey());
        String keyHash = sha256(key.toString());
        String fingerprint = keyedFingerprint(
                key, "account-deletion-complete\n" + command.requestId() + "\ntrue\n" + command.confirmation());
        AccountDeletionRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new NotFoundException("ACCOUNT_DELETION_REQUEST_NOT_FOUND", "탈퇴 요청을 찾을 수 없습니다"));
        if (request.getStatus() == AccountDeletionStatus.COMPLETED) {
            return toAdminResult(requests.complete(
                    request.getId(), "", command.actorId(), keyHash, fingerprint, Instant.now(clock)));
        }

        String accountId = request.getAccountId();
        Account account = accounts.findById(accountId)
                .orElseThrow(
                        () -> new ConflictException("ACCOUNT_DELETION_ACCOUNT_MISSING", "파기 대상 계정이 없어 처리를 중단했습니다"));
        if (!AccountDeletionRequest.suspensionReason(request.getId()).equals(account.getSuspendedReason())) {
            throw new ConflictException("ACCOUNT_DELETION_SUSPENSION_MISSING", "탈퇴 전용 계정 잠금이 유지되지 않아 파기를 중단했습니다");
        }

        // Mongo 원장에는 남지 않는 단기 인증·세션 데이터부터 폐기한다. Mongo 실패 시 재발급만 필요하며 개인정보 노출은 늘지 않는다.
        sessions.revokeAllForAccount(accountId);
        passwordResets.delete(account.getEmail());
        phoneVerifications.deleteAllForAccount(accountId);
        verifications.delete(accountId);

        AccountDeletionRequest completed = requests.complete(
                request.getId(), accountId, command.actorId(), keyHash, fingerprint, Instant.now(clock));
        return toAdminResult(completed);
    }

    private Account activeUser(String accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
        account.ensureNotSuspended();
        if (!account.isVerified() || account.getRole() != Role.USER) {
            throw new ForbiddenException("ACCOUNT_DELETION_NOT_ALLOWED", "현재 계정은 일반 탈퇴 경로를 사용할 수 없습니다");
        }
        return account;
    }

    private Account requireAdmin(String actorId) {
        Account account =
                accounts.findById(actorId).orElseThrow(() -> new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다"));
        account.ensureNotSuspended();
        if (account.getRole() != Role.ADMIN) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        return account;
    }

    private AccountDeletionRequest activeRequest(String requestId) {
        AccountDeletionRequest request = requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_DELETION_REQUEST_NOT_FOUND", "탈퇴 요청을 찾을 수 없습니다"));
        if (request.getStatus() == AccountDeletionStatus.COMPLETED || request.getAccountId() == null) {
            throw new ConflictException("ACCOUNT_DELETION_ALREADY_COMPLETED", "이미 완료된 탈퇴 요청입니다");
        }
        return request;
    }

    private void verifyAndConsume(String accountId, String code) {
        Challenge challenge = verifications.find(accountId).orElseThrow(AccountDeletionService::invalidVerification);
        if (challenge.failedAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            verifications.delete(accountId);
            throw invalidVerification();
        }
        if (!passwordHasher.matches(code, new PasswordHash(challenge.codeHash()))) {
            int attempts = verifications.incrementFailedAttempts(accountId);
            if (attempts < 0 || attempts >= MAX_VERIFICATION_ATTEMPTS) {
                verifications.delete(accountId);
            }
            throw invalidVerification();
        }
        if (!verifications.consume(accountId, challenge.codeHash())) {
            throw invalidVerification();
        }
    }

    private static void validateStrongConfirmation(Account account, Email email, String phrase) {
        if (!account.getEmail().equals(email)) {
            throw new BadRequestException("ACCOUNT_DELETION_EMAIL_MISMATCH", "현재 계정 이메일을 정확히 입력해 주세요");
        }
        if (!CONFIRMATION_PHRASE.equals(phrase)) {
            throw new BadRequestException("ACCOUNT_DELETION_CONFIRMATION_MISMATCH", "확인 문구를 정확히 입력해 주세요");
        }
    }

    private static void requireExactRequestConfirmation(String requestId, String confirmation) {
        if (!requestId.equals(confirmation)) {
            throw new BadRequestException("ACCOUNT_DELETION_CONFIRMATION_MISMATCH", "탈퇴 요청 ID를 정확히 입력해 주세요");
        }
    }

    private static UUID parseKey(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException exception) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key는 UUID 형식이어야 합니다");
        }
    }

    private static Email parseEmail(String raw) {
        try {
            return new Email(raw);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("INVALID_EMAIL", "올바른 이메일 형식이 아닙니다");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** UUID 멱등성 키 자체를 HMAC key로 써, 저장된 fingerprint만으로 이메일·코드를 추측할 수 없게 한다. */
    private static String keyedFingerprint(UUID key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.toString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        }
    }

    private static BadRequestException invalidVerification() {
        return new BadRequestException(
                "ACCOUNT_DELETION_VERIFICATION_INVALID", "본인확인 코드가 올바르지 않거나 만료되었습니다. 새 코드를 요청해 주세요");
    }

    private static RequestAccountDeletionUseCase.Result toUserResult(AccountDeletionRequest request) {
        return new RequestAccountDeletionUseCase.Result(
                request.getId(), request.getStatus(), request.getBlockers(), request.getRequestedAt());
    }

    private static ManageAccountDeletionRequestsUseCase.Result toAdminResult(AccountDeletionRequest request) {
        return new ManageAccountDeletionRequestsUseCase.Result(
                request.getId(),
                request.getStatus(),
                request.getBlockers(),
                request.getHoldReason(),
                request.getRequestedAt(),
                request.getUpdatedAt(),
                request.getCompletedAt(),
                request.getDeletionCounts());
    }
}
