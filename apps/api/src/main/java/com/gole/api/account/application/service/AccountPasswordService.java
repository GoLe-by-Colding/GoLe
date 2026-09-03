package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.ChangePasswordUseCase;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase;
import com.gole.api.account.application.port.in.RequestPasswordResetUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort.Challenge;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.exception.PasswordTooLongException;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 비밀번호 변경·재설정 전용 서비스. 회원가입 인증 상태와 challenge 저장소를 공유하지 않는다. */
@Service
public class AccountPasswordService
        implements ChangePasswordUseCase, RequestPasswordResetUseCase, ConfirmPasswordResetUseCase {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_RESET_ATTEMPTS = 5;
    private static final Duration RESET_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final AccountRepositoryPort accountRepository;
    private final PasswordHasherPort passwordHasher;
    private final PasswordResetChallengeStorePort resetStore;
    private final VerificationCodeGeneratorPort codeGenerator;
    private final VerificationCodeSenderPort codeSender;
    private final SessionStorePort sessionStore;
    private final Clock clock;

    public AccountPasswordService(
            AccountRepositoryPort accountRepository,
            PasswordHasherPort passwordHasher,
            PasswordResetChallengeStorePort resetStore,
            VerificationCodeGeneratorPort codeGenerator,
            VerificationCodeSenderPort codeSender,
            SessionStorePort sessionStore,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.resetStore = resetStore;
        this.codeGenerator = codeGenerator;
        this.codeSender = codeSender;
        this.sessionStore = sessionStore;
        this.clock = clock;
    }

    @Override
    public void change(ChangePasswordCommand command) {
        validatePassword(command.newPassword());
        Account account = accountRepository
                .findById(command.accountId())
                .orElseThrow(() -> new BadRequestException("ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다"));
        account.ensureNotSuspended();

        if (!passwordHasher.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new BadRequestException("CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다");
        }
        if (passwordHasher.matches(command.newPassword(), account.getPasswordHash())) {
            throw new BadRequestException("PASSWORD_UNCHANGED", "새 비밀번호는 현재 비밀번호와 달라야 합니다");
        }

        account.upgradePasswordHash(passwordHasher.hash(command.newPassword()));
        account.recordSuccessfulSignIn();
        accountRepository.save(account);
        sessionStore.revokeAllForAccount(account.getId());
    }

    @Override
    public void request(RequestPasswordResetCommand command) {
        Email email = new Email(command.email());
        Instant now = Instant.now(clock);

        // 등록 여부·인증 상태·정지 여부를 외부에 드러내지 않는다. 기존 challenge가 있으면 조용히 쿨다운한다.
        var existing = resetStore.find(email);
        if (existing.isPresent() && now.isBefore(existing.get().issuedAt().plus(RESET_RESEND_COOLDOWN))) {
            return;
        }

        var account = accountRepository.findByEmail(email);
        if (account.isEmpty() || !account.get().isVerified() || account.get().isSuspended()) {
            resetStore.delete(email);
            return;
        }

        String rawCode = codeGenerator.generateCode();
        PasswordHash codeHash = passwordHasher.hash(rawCode);
        resetStore.store(email, new Challenge(account.get().getId(), codeHash.value(), now, 0), RESET_TTL);
        codeSender.sendPasswordReset(email, new VerificationCode(rawCode, now));
    }

    @Override
    public void confirm(ConfirmPasswordResetCommand command) {
        validatePassword(command.newPassword());
        Email email = new Email(command.email());
        Challenge challenge = resetStore.find(email).orElseThrow(AccountPasswordService::invalidReset);

        if (challenge.failedAttempts() >= MAX_RESET_ATTEMPTS) {
            resetStore.delete(email);
            throw invalidReset();
        }
        if (!passwordHasher.matches(command.code(), new PasswordHash(challenge.codeHash()))) {
            int attempts = resetStore.incrementFailedAttempts(email);
            if (attempts < 0 || attempts >= MAX_RESET_ATTEMPTS) {
                resetStore.delete(email);
            }
            throw invalidReset();
        }

        Account account =
                accountRepository.findById(challenge.accountId()).orElseThrow(AccountPasswordService::invalidReset);
        if (!account.getEmail().equals(email) || !account.isVerified() || account.isSuspended()) {
            resetStore.delete(email);
            throw invalidReset();
        }
        if (passwordHasher.matches(command.newPassword(), account.getPasswordHash())) {
            throw new BadRequestException("PASSWORD_UNCHANGED", "새 비밀번호는 기존 비밀번호와 달라야 합니다");
        }
        if (!resetStore.consume(email, challenge.codeHash())) {
            throw invalidReset();
        }

        account.upgradePasswordHash(passwordHasher.hash(command.newPassword()));
        account.recordSuccessfulSignIn();
        accountRepository.save(account);
        sessionStore.revokeAllForAccount(account.getId());
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new WeakPasswordException();
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new PasswordTooLongException();
        }
    }

    private static BadRequestException invalidReset() {
        return new BadRequestException("PASSWORD_RESET_INVALID", "재설정 코드가 올바르지 않거나 만료되었습니다. 새 코드를 요청해 주세요");
    }
}
